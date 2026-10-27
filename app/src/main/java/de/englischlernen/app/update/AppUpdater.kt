package de.englischlernen.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import de.englischlernen.app.EnglishApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Aktualisiert die App direkt aus der App heraus: neue Version bei GitHub finden,
 * APK herunterladen und ueber den Android-PackageInstaller installieren.
 * Android verlangt dabei aus Sicherheitsgruenden eine Bestaetigung durch den Nutzer
 * (und einmalig die Erlaubnis "Apps aus dieser Quelle installieren"). Updates werden nur
 * installiert, wenn sie mit demselben Schluessel signiert sind wie die installierte App.
 */
class AppUpdater(
    private val context: Context,
    private val checker: UpdateChecker,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val version: String) : State
        data class Available(val version: String, val notes: String, val url: String) : State
        data class NeedsPermission(val version: String, val notes: String, val url: String) : State
        data class Downloading(val version: String, val progress: Float) : State
        data object Installing : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Stille Pruefung beim App-Start (Fehler werden nicht angezeigt). */
    fun checkQuietly() {
        if (_state.value !is State.Idle) return
        scope.launch {
            when (val r = checker.check()) {
                is UpdateChecker.Result.UpdateAvailable -> _state.value = State.Available(r.version, r.notes, r.downloadUrl)
                else -> {}
            }
        }
    }

    fun check() {
        _state.value = State.Checking
        scope.launch {
            _state.value = when (val r = checker.check()) {
                is UpdateChecker.Result.UpdateAvailable -> State.Available(r.version, r.notes, r.downloadUrl)
                is UpdateChecker.Result.UpToDate -> State.UpToDate(r.version)
                is UpdateChecker.Result.Failed -> State.Error(r.message)
            }
        }
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Oeffnet die Systemeinstellung "Unbekannte Apps installieren" fuer diese App. */
    fun openInstallPermissionSettings(from: Context) {
        from.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Nach Rueckkehr aus den Einstellungen: Berechtigung erneut pruefen. */
    fun onResume() {
        val s = _state.value
        if (s is State.NeedsPermission && canInstall()) _state.value = State.Available(s.version, s.notes, s.url)
    }

    fun downloadAndInstall() {
        val s = _state.value
        val (version, notes, url) = when (s) {
            is State.Available -> Triple(s.version, s.notes, s.url)
            is State.NeedsPermission -> Triple(s.version, s.notes, s.url)
            else -> return
        }
        if (!canInstall()) {
            _state.value = State.NeedsPermission(version, notes, url)
            return
        }
        _state.value = State.Downloading(version, 0f)
        scope.launch {
            try {
                val file = download(url, version)
                _state.value = State.Installing
                install(file)
            } catch (e: Exception) {
                _state.value = State.Error("Update fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}. Bitte erneut versuchen.")
            }
        }
    }

    private suspend fun download(url: String, version: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "update-$version.apk")
        val req = Request.Builder().url(url).header("Accept", "application/octet-stream").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Download-Fehler ${r.code}")
            val body = r.body
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) _state.value = State.Downloading(version, (read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (file.length() < 100_000) throw java.io.IOException("Datei unvollständig")
        file
    }

    private suspend fun install(file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Ab Android 12 koennen kuenftige Updates ohne Nachfrage laufen, wenn diese App sie installiert hat.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallFailed(message: String) {
        _state.value = State.Error(message)
    }

    internal fun onUserActionStarted() {
        _state.value = State.Installing
    }

    internal fun onUserCancelled() {
        // Zurueck zu "verfuegbar", damit man es erneut versuchen kann.
        check()
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "de.englischlernen.app.INSTALL_STATUS"
    }
}

/** Empfaengt Rueckmeldungen des PackageInstallers. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = (context.applicationContext as EnglishApp).container.updater
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                    updater.onUserActionStarted()
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {} // App wird vom System neu gestartet bzw. beendet.
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater.onUserCancelled()
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                updater.onInstallFailed("Das Update passt nicht zur installierten App (andere Signatur). Bitte App einmal neu installieren.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> updater.onInstallFailed("Nicht genug Speicherplatz für das Update.")
            else -> updater.onInstallFailed(
                "Installation fehlgeschlagen (${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Status $status"})."
            )
        }
    }
}
