"""Der Markier-Editor: alle Werkzeuge, Zoom, Rückgängig, PDF-Seiten, Textsuche.

Was ein Markier-Programm wie Whiteout kann, ist hier versammelt: Kästen, Markieren,
Unter-/Durchstreichen, Linien, Pfeile, Ellipsen, Text, Stempel, Verwischen — dazu
Seiten blättern bei PDFs und nach Text suchen.

Gespeichert wird immer als Bild (auch beim PDF): Bemaltes bleibt sonst unter dem
Originaltext und wäre mit Markieren und Kopieren wieder zu holen.
"""
from __future__ import annotations

from pathlib import Path

import tkinter as tk
from tkinter import filedialog, messagebox, simpledialog, ttk

from PIL import Image, ImageTk

from . import namen
from .markieren import ARTEN, STILE, STEMPEL, Form, baue_pdf, speichere, zeichne_formen

FARBEN = {"kasten": (0, 0, 0), "markieren": (255, 235, 0), "unterstreichen": (0, 0, 0),
          "durchstreichen": (200, 30, 30), "linie": (0, 0, 0), "pfeil": (30, 90, 200),
          "ellipse": (200, 30, 30), "text": (200, 30, 30), "stempel": (200, 30, 30),
          "verwischen": (0, 0, 0)}
ANZEIGE = {"kasten": "#000000", "markieren": "#ffeb00", "unterstreichen": "#000000",
           "durchstreichen": "#c81e1e", "linie": "#000000", "pfeil": "#1e5ac8",
           "ellipse": "#c81e1e", "text": "#c81e1e", "stempel": "#c81e1e",
           "verwischen": "#777777"}
SKALIERUNG = 2.0


class Dokument:
    """Bild oder PDF, seitenweise als Bild — plus die Formen je Seite."""

    def __init__(self, pfad: Path):
        self.pfad = pfad
        self.ist_pdf = pfad.suffix.lower() == ".pdf"
        self.seiten: list[Image.Image] = []
        self.formen: list[list[Form]] = []
        # Drehen/Ziehen an den Endpunkten (Julius, 25.11.2026: „drehen am Endpunkt")
        self.griff: str | None = None
        self.seiten_masse: list[tuple[float, float]] = []
        if self.ist_pdf:
            try:
                import pymupdf as fitz
            except ImportError:
                import fitz
            with fitz.open(pfad) as dokument:
                for seite in dokument:
                    pixmap = seite.get_pixmap(matrix=fitz.Matrix(SKALIERUNG, SKALIERUNG), alpha=False)
                    bild = Image.frombytes("RGB", (pixmap.width, pixmap.height), pixmap.samples)
                    self.seiten.append(bild)
                    self.formen.append([])
                    self.seiten_masse.append((seite.rect.width, seite.rect.height))
        else:
            self.seiten.append(Image.open(pfad).convert("RGB"))
            self.formen.append([])
            self.seiten_masse.append((0.0, 0.0))


class Editor:
    def __init__(self, pfad: Path, sprache: str = "de"):
        self.dokument = Dokument(pfad)
        self.seite = 0
        self.werkzeug = "kasten"
        self.zoom = 1.0
        self.start: tuple[int, int] | None = None
        self.vorschau: int | None = None
        self.auswahl: int | None = None      # angeklicktes Objekt (Index in der Liste)
        self.versatz: tuple[int, int] = (0, 0)
        self.zielpunkt: tuple[int, int] | None = None

        self.fenster = tk.Toplevel()
        self.fenster.title("Markieren — " + pfad.name)
        self.fenster.geometry("1100x760")

        # Werkzeugleiste, zwei Reihen
        leiste = ttk.Frame(self.fenster, padding=6)
        leiste.pack(fill="x")
        for i, (wert, text) in enumerate(ARTEN.items()):
            ttk.Button(leiste, text=text, width=16,
                       command=lambda w=wert: self.setze_werkzeug(w)
                       ).grid(row=i // 5, column=i % 5, padx=2, pady=2, sticky="ew")

        # Zweite Leiste: Stil fürs Verwischen, Schriftgröße, Stempel-Text
        extras = ttk.Frame(self.fenster, padding=6)
        extras.pack(fill="x")
        # Die drei Felder werden je Werkzeug aus- oder eingeschaltet. Vorher standen sie
        # immer bedienbar da, auch wenn sie nichts bewirkten (Julius, 25.11.2026; in der
        # macOS-App zuerst gefunden, hier nachgezogen).
        ttk.Label(extras, text="Verwisch-Stil:").pack(side="left", padx=(0, 4))
        self.stil = tk.StringVar(value="weich")
        self.feld_stil = ttk.Combobox(extras, textvariable=self.stil, values=list(STILE.values()),
                                      width=14, state="readonly")
        self.feld_stil.pack(side="left", padx=(0, 12))
        ttk.Label(extras, text="Schriftgröße:").pack(side="left", padx=(0, 4))
        self.groesse = tk.StringVar(value="18")
        self.feld_groesse = ttk.Combobox(extras, textvariable=self.groesse,
                                         values=["12", "16", "18", "24", "32", "48"],
                                         width=5, state="readonly")
        self.feld_groesse.pack(side="left", padx=(0, 12))
        ttk.Label(extras, text="Stempel:").pack(side="left", padx=(0, 4))
        self.stempel_wahl = tk.StringVar(value=STEMPEL[0])
        self.feld_stempel = ttk.Combobox(extras, textvariable=self.stempel_wahl, values=STEMPEL,
                                         width=14, state="readonly")
        self.feld_stempel.pack(side="left")

        unten = ttk.Frame(self.fenster, padding=6)
        unten.pack(fill="x")
        ttk.Button(unten, text="Rückgängig", command=self.rueckgaengig).pack(side="left", padx=2)
        ttk.Button(unten, text="Alles löschen", command=self.leeren).pack(side="left", padx=2)
        ttk.Button(unten, text="−", width=3, command=lambda: self.zoome(1 / 1.25)).pack(side="left", padx=(16, 2))
        ttk.Button(unten, text="+", width=3, command=lambda: self.zoome(1.25)).pack(side="left", padx=2)
        ttk.Button(unten, text="Speichern", command=self.speichern).pack(side="right", padx=2)

        if self.dokument.ist_pdf:
            self.seiten_label = ttk.Label(unten, text="")
            self.seiten_label.pack(side="left", padx=12)
            ttk.Button(unten, text="◀", width=3, command=lambda: self.blaettern(-1)).pack(side="left", padx=2)
            ttk.Button(unten, text="▶", width=3, command=lambda: self.blaettern(1)).pack(side="left", padx=2)
            ttk.Label(unten, text="Suchen:").pack(side="left", padx=(16, 2))
            self.suchfeld = ttk.Entry(unten, width=22)
            self.suchfeld.pack(side="left")
            ttk.Button(unten, text="Finden", command=self.suchen).pack(side="left", padx=2)

        rahmen = ttk.Frame(self.fenster)
        rahmen.pack(fill="both", expand=True)
        self.leinwand = tk.Canvas(rahmen, background="#222222", highlightthickness=0)
        self.leinwand.pack(side="left", fill="both", expand=True)
        balken = ttk.Scrollbar(rahmen, orient="vertical", command=self.leinwand.yview)
        balken.pack(side="right", fill="y")
        self.leinwand.configure(yscrollcommand=balken.set)

        self.leinwand.bind("<ButtonPress-1>", self.starten)
        self.leinwand.bind("<B1-Motion>", self.ziehen)
        self.leinwand.bind("<ButtonRelease-1>", self.loslassen)
        self.fenster.bind("<BackSpace>", self.entferne_auswahl)
        self.fenster.bind("<Delete>", self.entferne_auswahl)

        self.felder_schalten()
        self.zeichne()
        self.fenster.wait_window()

    # ---------------------------------------------------------------- Werkzeug

    def felder_schalten(self) -> None:
        """Schaltet die Felder je Werkzeug: ein Feld, das nichts bewirkt, ist grau.

        Zuordnung, am Zeichenweg abgelesen: Verwisch-Stil nur beim Verwischen,
        Schriftgröße und Stempel nur bei Text und Stempel.
        """
        paare = ((getattr(self, "feld_stil", None), self.werkzeug == "verwischen"),
                 (getattr(self, "feld_groesse", None), self.werkzeug in ("text", "stempel")),
                 (getattr(self, "feld_stempel", None), self.werkzeug == "stempel"))
        for feld, an in paare:
            if feld is None:
                continue
            try:
                feld.configure(state="readonly" if an else "disabled")
            except Exception:
                pass

    def setze_werkzeug(self, wert: str) -> None:
        self.werkzeug = wert
        self.felder_schalten()

    def zoome(self, faktor: float) -> None:
        self.zoom = max(0.2, min(5.0, self.zoom * faktor))
        self.zeichne()

    def blaettern(self, richtung: int) -> None:
        neu = self.seite + richtung
        if 0 <= neu < len(self.dokument.seiten):
            self.seite = neu
            self.zeichne()

    # ---------------------------------------------------------------- Maus

    def bildpunkt(self, x: int, y: int) -> tuple[int, int]:
        """Fensterkoordinaten in Bildpixel (Zoom und Verschiebung herausrechnen)."""
        x0 = int(self.leinwand.canvasx(0))
        y0 = int(self.leinwand.canvasy(0))
        return (int((x + x0) / self.zoom), int((y + y0) / self.zoom))

    def trifft(self, form: Form, x: int, y: int, rand: int = 6) -> bool:
        """Liegt der Punkt (Bildpixel) auf diesem Objekt? Für Auswahl und Verschieben."""
        links, rechts = sorted((form.x1, form.x2))
        oben, unten = sorted((form.y1, form.y2))
        return (links - rand <= x <= rechts + rand) and (oben - rand <= y <= unten + rand)

    def starten(self, ereignis) -> None:
        x, y = self.bildpunkt(ereignis.x, ereignis.y)
        # Zuerst die Griffe: liegen sie unter dem Zeiger, wird gezogen statt verschoben.
        # Trefferfläche 14 Bildpixel — bei starker Vergrößerung entsprechend kleiner.
        for nummer in range(len(self.dokument.formen[self.seite]) - 1, -1, -1):
            form = self.dokument.formen[self.seite][nummer]
            for name, (gx, gy) in (("ende", (form.x2, form.y2)),
                                   ("anfang", (form.x1, form.y1))):
                if (gx - x) ** 2 + (gy - y) ** 2 <= 14 ** 2:
                    self.auswahl = nummer
                    self.griff = name
                    self.zeichne()
                    return
        self.griff = None
        # dann sehen, ob ein vorhandenes Objekt angeklickt wurde — von oben nach unten
        for nummer in range(len(self.dokument.formen[self.seite]) - 1, -1, -1):
            form = self.dokument.formen[self.seite][nummer]
            if self.trifft(form, x, y):
                self.auswahl = nummer
                self.versatz = (x - form.x1, y - form.y1)
                self.zielpunkt = (x, y)
                return
        self.auswahl = None
        self.start = (x, y)

    def ziehen(self, ereignis) -> None:
        if self.auswahl is not None and self.griff is not None:
            x, y = self.bildpunkt(ereignis.x, ereignis.y)
            form = self.dokument.formen[self.seite][self.auswahl]
            if self.griff == "ende":
                form.x2, form.y2 = x, y
            else:
                form.x1, form.y1 = x, y
            self.zeichne()
            return
        if self.auswahl is not None:
            # Objekt verschieben
            x, y = self.bildpunkt(ereignis.x, ereignis.y)
            form = self.dokument.formen[self.seite][self.auswahl]
            breite = form.x2 - form.x1
            hoehe = form.y2 - form.y1
            form.x1, form.y1 = x - self.versatz[0], y - self.versatz[1]
            form.x2, form.y2 = form.x1 + breite, form.y1 + hoehe
            self.zeichne()
            return
        if self.start is None:
            return
        if self.vorschau is not None:
            self.leinwand.delete(self.vorschau)
        x1, y1 = self.start
        x2, y2 = self.bildpunkt(ereignis.x, ereignis.y)
        self.vorschau = self.leinwand.create_rectangle(
            x1 * self.zoom, y1 * self.zoom, x2 * self.zoom, y2 * self.zoom,
            outline=ANZEIGE.get(self.werkzeug, "#000000"), width=2)

    def loslassen(self, ereignis) -> None:
        if self.griff is not None:
            self.griff = None
            self.zeichne()
            return
        if self.auswahl is not None:
            self.auswahl = None
            self.zeichne()
            return
        if self.start is None:
            return
        x1, y1 = self.start
        x2, y2 = self.bildpunkt(ereignis.x, ereignis.y)
        self.start = None
        if self.vorschau is not None:
            self.leinwand.delete(self.vorschau)
            self.vorschau = None
        if abs(x2 - x1) < 3 and abs(y2 - y1) < 3:
            return
        farbe = FARBEN.get(self.werkzeug, (0, 0, 0))
        feld = {"groesse": int(self.groesse.get() or 18)}
        if self.werkzeug == "verwischen":
            umkehr = {v: k for k, v in STILE.items()}
            feld["stil"] = umkehr.get(self.stil.get(), "weich")
        if self.werkzeug == "stempel":
            text = self.stempel_wahl.get()
            self.dokument.formen[self.seite].append(
                Form("stempel", min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2),
                     text=text, farbe=farbe, dicke=4, feld=feld))
        elif self.werkzeug == "text":
            text = simpledialog.askstring("Text", "Welcher Text?", parent=self.fenster)
            if not text:
                return
            self.dokument.formen[self.seite].append(
                Form("text", min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2),
                     text=text, farbe=farbe, dicke=4, feld=feld))
        else:
            self.dokument.formen[self.seite].append(
                Form(self.werkzeug, x1, y1, x2, y2, farbe=farbe, dicke=4, feld=feld))
        self.zeichne()

    def entferne_auswahl(self, _ereignis=None) -> None:
        if self.auswahl is not None:
            del self.dokument.formen[self.seite][self.auswahl]
            self.auswahl = None
            self.zeichne()

    def rueckgaengig(self) -> None:
        if self.dokument.formen[self.seite]:
            self.dokument.formen[self.seite].pop()
            self.zeichne()

    def leeren(self) -> None:
        self.dokument.formen[self.seite].clear()
        self.zeichne()

    # ---------------------------------------------------------------- Suche

    def suchen(self) -> None:
        begriff = self.suchfeld.get().strip()
        if not begriff:
            return
        try:
            import pymupdf as fitz
        except ImportError:
            import fitz
        try:
            with fitz.open(self.dokument.pfad) as dokument:
                seite = dokument[self.seite]
                treffer = seite.search_for(begriff)
        except Exception as fehler:
            messagebox.showerror("Suchen", str(fehler), parent=self.fenster)
            return
        if not treffer:
            messagebox.showinfo("Suchen", "Nicht gefunden auf dieser Seite: " + begriff,
                               parent=self.fenster)
            return
        for kasten in treffer:
            self.dokument.formen[self.seite].append(
                Form("kasten", int(kasten.x0 * SKALIERUNG), int(kasten.y0 * SKALIERUNG),
                     int(kasten.x1 * SKALIERUNG), int(kasten.y1 * SKALIERUNG)))
        self.zeichne()
        messagebox.showinfo("Suchen", "%d Treffer geschwärzt." % len(treffer), parent=self.fenster)

    # ---------------------------------------------------------------- Anzeige

    def zeichne(self) -> None:
        bild = self.dokument.seiten[self.seite]
        breite = max(1, int(bild.width * self.zoom))
        hoehe = max(1, int(bild.height * self.zoom))
        klein = bild.resize((breite, hoehe), Image.LANCZOS)
        formen = [
            Form(f.art, int(f.x1 * self.zoom), int(f.y1 * self.zoom),
                 int(f.x2 * self.zoom), int(f.y2 * self.zoom),
                 text=f.text, farbe=f.farbe, dicke=4, feld=dict(f.feld))
            for f in self.dokument.formen[self.seite]
        ]
        markiert = zeichne_formen(klein, formen)
        self.anzeige = ImageTk.PhotoImage(markiert)
        self.leinwand.delete("all")
        self.leinwand.create_image(0, 0, anchor="nw", image=self.anzeige)
        self.leinwand.configure(scrollregion=(0, 0, breite, hoehe))
        # Griffe: je Form ein Punkt am Anfang (orange) und am Ende (blau) —
        # nur bei Auswahl, damit die Vorschau ruhig bleibt.
        if self.auswahl is not None:
            form = self.dokument.formen[self.seite][self.auswahl]
            for farbe, (gx, gy) in (("#ff9600", (form.x1, form.y1)),
                                    ("#0096ff", (form.x2, form.y2))):
                r = 7
                self.leinwand.create_oval((gx * self.zoom) - r, (gy * self.zoom) - r,
                                          (gx * self.zoom) + r, (gy * self.zoom) + r,
                                          fill=farbe, outline="white", width=2)
        if self.dokument.ist_pdf:
            self.seiten_label.config(text="Seite %d von %d" % (self.seite + 1,
                                                               len(self.dokument.seiten)))

    # ---------------------------------------------------------------- Speichern

    def speichern(self) -> None:
        endung = ".pdf" if self.dokument.ist_pdf else ".png"
        # Bei Bildern drei Formate zur Wahl (Julius, 27.11.2026). Bei PDFs bleibt es PDF —
        # eine mehrseitige Markierung als JPEG wäre keine Markierung mehr.
        arten = ([("PDF", "*.pdf")] if self.dokument.ist_pdf
                 else [("PNG-Bild", "*.png"), ("JPEG-Bild", "*.jpg *.jpeg"), ("PDF", "*.pdf")])
        ziel = filedialog.asksaveasfilename(
            parent=self.fenster, title="Speichern als", defaultextension=endung,
            filetypes=arten,
            initialfile=self.dokument.pfad.stem + namen.zusatz(self.sprache) + endung)
        if not ziel:
            return
        if self.dokument.ist_pdf:
            self.speichere_pdf(Path(ziel))
        else:
            fertig = zeichne_formen(self.dokument.seiten[0], self.dokument.formen[0])
            speichere(fertig, Path(ziel))
        messagebox.showinfo("Gespeichert", str(ziel), parent=self.fenster)

    def speichere_pdf(self, ziel: Path) -> None:
        """Jede Seite als fertiges Bild in ein neues PDF — nicht durchsuchbar, dafür sicher."""
        baue_pdf(self.dokument.seiten, self.dokument.formen, self.dokument.seiten_masse, ziel)


def oeffne(pfad: Path, sprache: str = "de") -> None:
    Editor(pfad, sprache)


def selbsttest() -> int:
    """Prüfung ohne Fenster: Umrechnung, Speichern, PDF-Seiten und Suche."""
    import tempfile

    fehler: list[str] = []

    klasse = type("Probe", (), {"zoom": 2.0, "leinwand": type("L", (), {
        "canvasx": lambda self, x: 0, "canvasy": lambda self, y: 0})()})
    x, y = Editor.bildpunkt(klasse, 600, 400)
    if (x, y) != (300, 200):
        fehler.append("Umrechnung: erwartet (300, 200), bekommen (%d, %d)" % (x, y))

    with tempfile.TemporaryDirectory() as ordner:
        quelle = Path(ordner) / "probe.png"
        Image.new("RGB", (400, 200), "white").save(quelle)
        bild = Image.open(quelle).convert("RGB")
        fertig = zeichne_formen(bild, [Form("kasten", 50, 40, 150, 90)])
        ziel = Path(ordner) / "fertig.png"
        speichere(fertig, ziel)
        geprueft = Image.open(ziel).convert("RGB")
        if geprueft.getpixel((100, 65)) != (0, 0, 0):
            fehler.append("Kasten ist im gespeicherten Bild nicht schwarz")
        if geprueft.getpixel((300, 150)) != (255, 255, 255):
            fehler.append("außerhalb des Kastens wurde mitgeschwärzt")

        # PDF mit Suchtext: „Krause" muß gefunden werden
        pdf = Path(ordner) / "probe.pdf"
        try:
            import pymupdf as fitz
        except ImportError:
            import fitz
        dokument = fitz.open()
        seite = dokument.new_page()
        seite.insert_text((72, 120), "Julius Krause, Freiburg", fontsize=14)
        dokument.save(pdf)
        dokument.close()
        with fitz.open(pdf) as wieder:
            treffer = wieder[0].search_for("Krause")
        if len(treffer) != 1:
            fehler.append("Suche im PDF: erwartet 1 Treffer, bekommen %d" % len(treffer))

    # 3) Treffer-Prüfung für Auswahl und Verschieben
    treffer = Form("kasten", 100, 100, 200, 150)
    for punkt, erwartet in [((150, 120), True), ((95, 120), True), ((300, 300), False)]:
        if Editor.trifft(None, treffer, punkt[0], punkt[1]) != erwartet:
            fehler.append("trifft%s: erwartet %s" % (punkt, erwartet))

    # 4) Die drei Verwisch-Stile müssen sich unterscheiden
    grund = Image.new("RGB", (300, 120), "white")
    for x in range(150, 300):
        for y in range(40, 80):
            grund.putpixel((x, y), (0, 0, 0))
    bilder = {}
    for stil in ("weich", "mosaik", "pixel"):
        bilder[stil] = zeichne_formen(grund, [Form("verwischen", 10, 10, 290, 110,
                                                   feld={"stil": stil, "staerke": 12})])
    if len({b.tobytes() for b in bilder.values()}) != 3:
        fehler.append("Verwisch-Stile sehen gleich aus")

    # 5) Schriftgröße muß das Bild verändern
    klein = zeichne_formen(grund, [Form("text", 10, 10, 0, 0, text="Probe", feld={"groesse": 12})])
    gross = zeichne_formen(grund, [Form("text", 10, 10, 0, 0, text="Probe", feld={"groesse": 40})])
    if klein.tobytes() == gross.tobytes():
        fehler.append("Schriftgröße wirkt nicht")

    if fehler:
        for eintrag in fehler:
            print("  FEHLER:", eintrag)
        return 1
    print("  Selbsttest in Ordnung: Umrechnung, Speichern, PDF-Suche, Auswahl, Stile, Schriftgröße")
    return 0
