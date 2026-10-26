package de.englischlernen.app.update

import de.englischlernen.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Prueft ueber die GitHub-API (HTTPS), ob eine neuere Version veroeffentlicht wurde.
 * Benoetigt Internet. Es wird nichts automatisch installiert – der Nutzer entscheidet.
 */
class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    sealed interface Result {
        data class UpdateAvailable(val version: String, val notes: String, val downloadUrl: String, val pageUrl: String) : Result
        data class UpToDate(val version: String) : Result
        data class Failed(val message: String) : Result
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String,
        @SerialName("html_url") val htmlUrl: String,
        val body: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val url: String)

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Failed("Server antwortet mit Fehler ${resp.code}.")
                val release = json.decodeFromString(Release.serializer(), resp.body.string())
                val remote = release.tag.removePrefix("v")
                val local = BuildConfig.VERSION_NAME.substringBefore('-')
                if (isNewer(remote, local)) {
                    val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    Result.UpdateAvailable(remote, release.body.orEmpty(), apk?.url ?: release.htmlUrl, release.htmlUrl)
                } else Result.UpToDate(local)
            }
        } catch (e: Exception) {
            Result.Failed("Keine Verbindung möglich. Bitte Internet prüfen.")
        }
    }

    companion object {
        fun isNewer(remote: String, local: String): Boolean {
            val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
            val l = local.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
