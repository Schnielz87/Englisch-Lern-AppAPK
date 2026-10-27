// Erzeugt docs/Anleitung_Fundbrueder_English.docx aus app/src/main/assets/content/help.json
// (dieselbe Quelle wie der Hilfe-Bereich in der App). Aufruf: node tools/make_manual.js
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, ImageRun, Table, TableRow, TableCell,
  WidthType, ShadingType, BorderStyle, LevelFormat, PageBreak, Footer, PageNumber, Header,
} = require("docx");

const root = path.join(__dirname, "..");
const help = JSON.parse(fs.readFileSync(path.join(root, "app/src/main/assets/content/help.json"), "utf8"));
const gradle = fs.readFileSync(path.join(root, "app/build.gradle.kts"), "utf8");
const version = (gradle.match(/appVersionName = "([^"]+)"/) || [])[1] || "";
const repo = "https://github.com/Schnielz87/Englisch-Lern-AppAPK";

const GREEN = "2F5D40", GOLD = "C9962B", DARK = "1C1B17";
const CONTENT_W = 9026; // A4 mit 2,54 cm Rand (DXA)

function pngSize(file) {
  const b = fs.readFileSync(file);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20), data: b };
}
function image(file, widthPx, caption) {
  const { w, h, data } = pngSize(file);
  const out = [new Paragraph({
    alignment: AlignmentType.CENTER, spacing: { before: 120, after: 60 },
    children: [new ImageRun({ type: "png", data, transformation: { width: widthPx, height: Math.round(h * widthPx / w) },
      altText: { title: caption || "Abbildung", description: caption || "Abbildung", name: path.basename(file) } })],
  })];
  if (caption) out.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 },
    children: [new TextRun({ text: caption, italics: true, size: 18, color: "6B6455" })] }));
  return out;
}
const body = (t) => new Paragraph({ spacing: { after: 120 }, children: [new TextRun(t)] });
const border = { style: BorderStyle.SINGLE, size: 4, color: "D9D0BC" };
const borders = { top: border, bottom: border, left: border, right: border };

function table(rows) {
  const widths = [2600, CONTENT_W - 2600];
  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA }, columnWidths: widths,
    rows: rows.map((r, i) => new TableRow({
      tableHeader: i === 0,
      children: r.map((cell, j) => new TableCell({
        borders, width: { size: widths[j], type: WidthType.DXA },
        shading: { fill: i === 0 ? GREEN : (i % 2 ? "FFFFFF" : "F6F1E4"), type: ShadingType.CLEAR, color: "auto" },
        margins: { top: 80, bottom: 80, left: 120, right: 120 },
        children: [new Paragraph({ children: [new TextRun({ text: cell, bold: i === 0 || j === 0, color: i === 0 ? "FFFFFF" : DARK })] })],
      })),
    })),
  });
}

let stepsInstance = 0;
const children = [];

// Titelseite
children.push(...image(path.join(root, "docs/logo.png"), 200));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 200 },
  children: [new TextRun({ text: "Fundbrüder English", bold: true, size: 56, color: GREEN })] }));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 120 },
  children: [new TextRun({ text: "Anleitung zur App", size: 32, color: DARK })] }));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 120 },
  children: [new TextRun({ text: "Timo for you kleiner SchwaLu", italics: true, size: 24, color: GOLD })] }));
children.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 600 },
  children: [new TextRun({ text: `Stand: App-Version ${version}`, size: 22, color: "6B6455" })] }));
children.push(new Paragraph({ alignment: AlignmentType.CENTER,
  children: [new TextRun({ text: `Download: ${repo}/releases/latest`, size: 20, color: "6B6455" })] }));
children.push(new Paragraph({ children: [new PageBreak()] }));

// Inhaltsverzeichnis
children.push(new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun("Inhalt")] }));
// Feste Kapitelliste (ein automatisches Verzeichnis bleibt auf Handys/Google Docs oft leer)
["Installation", ...help.map((h) => h.title)].forEach((t, i) => children.push(new Paragraph({
  spacing: { after: 100 }, children: [new TextRun({ text: `${i + 1}.  `, bold: true, color: GOLD }), new TextRun({ text: t, size: 26 })],
})));
children.push(new Paragraph({ children: [new PageBreak()] }));

// Kapitel 0: Installation (nur in der Word-Anleitung)
children.push(new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun("1.  Installation")] }));
children.push(body("Die App gibt es kostenlos als Datei (APK) auf GitHub – ohne Play Store, ohne Konto."));
stepsInstance++;
[
  `Auf dem Android-Handy diesen Link öffnen: ${repo}/releases/latest/download/FundbruederEnglish.apk`,
  "Den Hinweis „Datei herunterladen?“ mit „Herunterladen“ bestätigen.",
  "Nach dem Download die Datei öffnen. Fragt Android nach der Erlaubnis für „unbekannte Apps“: „Einstellungen“ antippen, „Aus dieser Quelle zulassen“ einschalten und zurückgehen.",
  "Auf „Installieren“ tippen. Die App erscheint als „Fundbrüder English“ auf dem Startbildschirm.",
].forEach((t) => children.push(new Paragraph({ numbering: { reference: "steps", level: 0, instance: stepsInstance }, spacing: { after: 80 }, children: [new TextRun(t)] })));
children.push(body("Voraussetzung: Android 8.0 oder neuer. Spätere Updates installierst du direkt in der App (siehe Kapitel „Updates installieren“)."));

help.forEach((s, idx) => {
  children.push(new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: idx > 0 && s.blocks.some((b) => b.image), children: [new TextRun(`${idx + 2}.  ${s.title}`)] }));
  for (const b of s.blocks) {
    if (b.p) children.push(body(b.p));
    if (b.steps) {
      stepsInstance++;
      b.steps.forEach((t) => children.push(new Paragraph({ numbering: { reference: "steps", level: 0, instance: stepsInstance }, spacing: { after: 80 }, children: [new TextRun(t)] })));
    }
    if (b.bullets) b.bullets.forEach((t) => children.push(new Paragraph({ numbering: { reference: "bullets", level: 0 }, spacing: { after: 80 }, children: [new TextRun(t)] })));
    if (b.table) { children.push(table(b.table)); children.push(new Paragraph({ spacing: { after: 120 }, children: [] })); }
    if (b.qa) b.qa.forEach(([q, a]) => {
      children.push(new Paragraph({ spacing: { before: 120, after: 40 }, keepNext: true, children: [new TextRun({ text: q, bold: true, color: GREEN })] }));
      children.push(body(a));
    });
    if (b.image) {
      const f = path.join(root, "docs/manual", b.image);
      if (fs.existsSync(f)) children.push(...image(f, b.image === "help_update.png" ? 440 : 230, b.caption));
    }
  }
});

const doc = new Document({
  creator: "Fundbrüder English", title: "Anleitung Fundbrüder English", description: `Anleitung zur App, Version ${version}`,
  styles: {
    default: { document: { run: { font: "Calibri", size: 23, color: DARK } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 34, bold: true, color: GREEN, font: "Calibri" },
        paragraph: { spacing: { before: 360, after: 160 }, outlineLevel: 0,
          border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: GOLD, space: 4 } } } },
    ],
  },
  numbering: { config: [
    { reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
    { reference: "steps", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: 720, hanging: 360 } } } }] },
  ] },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, bottom: 1440, left: 1440, right: 1440 } } },
    headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT,
      children: [new TextRun({ text: "Fundbrüder English – Anleitung", size: 18, color: "8A8170" })] })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
      children: [new TextRun({ children: ["Seite ", PageNumber.CURRENT], size: 18, color: "8A8170" })] })] }) },
    children,
  }],
});

Packer.toBuffer(doc).then((buf) => {
  const out = path.join(root, "docs/Anleitung_Fundbrueder_English.docx");
  fs.writeFileSync(out, buf);
  console.log("geschrieben:", out, buf.length, "Bytes");
});
