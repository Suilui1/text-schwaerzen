"""Die Oberfläche (Tkinter) — Auswahl, Wortliste, Rahmen, Fortschritt.

Tkinter statt Qt: es steckt in Python schon drin, ist auf Windows, Linux und macOS gleich
und macht das spätere Paket klein.
"""
from __future__ import annotations

import queue
import threading
from pathlib import Path

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from PIL import Image, ImageTk

from . import regeln
from .erkennung import erkenne_worte
from .masken import schwaeze_datei
from .pdf import schwaeze_pdf
from .__main__ import lade_einstellungen, speichere_einstellungen, ziel_pfad, bericht

TEXTE = {
    "de": {"k_oeffnen": "Öffnen", "k_loeschen": "Löschen", "k_zeigen": "Im Dateimanager zeigen", "k_ergebnisse": "Ergebnisse", "keine_ergebnisse": "Noch keine geschwärzte Datei in der Ablage.", "loeschen_frage": "Endgültig entfernen?", "k_datei": "Datei wählen", "k_letzte": "Letzte Dateien", "keine_letzte": "Noch keine Datei geöffnet", "k_text": "Textdatei schwärzen", "k_info": "Info", "info_text": """Text schwärzen — was die App tut

Schwärzt Text in Bildern, PDFs und Textdateien. Alles geschieht auf diesem Rechner; nichts wird verschickt.

ERKENNUNG
Tesseract mit deutscher Sprachdatei. Weitere Sprachen über eine eigene Sprachdatei (tessdata).

SCHWÄRZEN
· Nur Adressen und Namen — nach Regeln (E-Mail, Telefon, IBAN, Adressen, Buchungsnummern)
· Alles — jede erkannte Zeile
· Nur markierte Wörter — Wörter ankreuzen
· Rahmen ziehen — Bereich selbst bestimmen
· Markieren von Hand — Rechtecke, Stempel, Stile

BEI PDFS
Jede Seite wird als Bild gerendert und neu geschrieben. Der Text ist danach nicht mehr markierbar oder kopierbar.

GRENZE
Ein Blick auf das Ergebnis bleibt nötig. Namen ohne Adresse oder Nummer erkennt die Automatik nicht sicher.

Quelltext: github.com/Suilui1/text-schwaerzen""", "titel": "Text schwärzen",
           # Zwei Schritte statt vier Knöpfe auf einmal — wie die macOS-Fassung seit
           # 25.11.2026 (Julius: „user experience hat Prioritaet, alles so klar und
           # einfach wie noetig").
           "wie_vorgehen": "Wie vorgehen?", "was_schwaerzen": "Was schwärzen?",
           "k_auto": "Automatisch schwärzen", "k_selbst": "Selbst markieren",
           "k_gezielt": "Nur Adressen und Namen", "k_alles": "Alles",
           "k_worte": "Nur markierte Wörter", "k_rahmen": "Rahmen ziehen",
           "k_abbrechen": "Abbrechen", "k_schwaerzen": "Schwärzen",
           "k_alle": "Alle", "k_keine": "Keine", "k_ablage": "Ablage wählen…",
           "ablage_original": "Ablage: neben dem Original",
           "wort_titel": "Wörter auswählen",
           "wort_text": "Angekreuzte Wörter werden geschwärzt — im Bild an ihrer Stelle, im PDF auf allen Seiten.",
           # Eigener Text fürs Startfenster: dort stand bis 25.11.2026 der
           # Wortauswahl-Text („Angekreuzte Wörter …") — falsch an dieser Stelle.
           "start_text": "Schwärzt Text in Bildern, PDFs und Textdateien. Alles geschieht auf diesem Rechner; nichts wird verschickt.",
           "rahmen_titel": "Rahmen um das ziehen, was LESBAR bleiben soll — Enter schwärzt den Rest",
           "seite": "Seite", "von": "von", "fertig": "Fertig",
           "bild_waehlen": "Welches Bild oder PDF schwärzen?"},
    "en": {"k_oeffnen": "Open", "k_loeschen": "Delete", "k_zeigen": "Show in file manager", "k_ergebnisse": "Results", "keine_ergebnisse": "No redacted file in the folder yet.", "loeschen_frage": "Remove permanently?", "k_datei": "Choose file", "k_letzte": "Recent files", "keine_letzte": "No file opened yet", "k_text": "Redact a text file", "k_info": "Info", "info_text": """Redact text — what this app does

Redacts text in images, PDFs and text files. Everything happens on this machine; nothing is sent anywhere.

RECOGNITION
Tesseract with the German language file. Other languages need their own language file (tessdata).

REDACTION
· Addresses and names only — by rules (email, phone, IBAN, addresses, booking numbers)
· Everything — every recognised line
· Marked words only — tick words
· Draw a box — decide the area yourself
· Mark by hand — rectangles, stamps, styles

PDFs
Every page is rendered as an image and rewritten. The text can no longer be selected or copied.

LIMIT
Always look at the result. Names without an address or number are not reliably detected.

Source: github.com/Suilui1/text-schwaerzen""", "titel": "Redact text",
           "wie_vorgehen": "How to proceed?", "was_schwaerzen": "What to redact?",
           "k_auto": "Redact automatically", "k_selbst": "Mark it myself",
           "k_gezielt": "Addresses and names only", "k_alles": "Everything",
           "k_worte": "Selected words only", "k_rahmen": "Draw a box",
           "k_abbrechen": "Cancel", "k_schwaerzen": "Redact",
           "k_alle": "All", "k_keine": "None", "k_ablage": "Choose folder…",
           "ablage_original": "Saved next to the original",
           "wort_titel": "Choose words",
           "wort_text": "Checked words are redacted — in place in images, on every page in PDFs.",
           "start_text": "Redacts text in images, PDFs and text files. Everything happens on this machine; nothing is sent anywhere.",
           "rahmen_titel": "Draw a box around what should stay READABLE — Enter redacts the rest",
           "seite": "Page", "von": "of", "fertig": "Done",
           "bild_waehlen": "Which image or PDF should be redacted?"},
}


def sprache_waehlen() -> str:
    """Deutsch erkennen — auf allen drei Systemen.

    Auf macOS lieferte `locale.getlocale()` nichts, deshalb erschien die Oberfläche
    englisch (24.11.2026 gesehen). Jetzt werden Umgebung, Systemsprache und der
    Windows-Name („German_Germany") geprüft.
    """
    import locale
    import os
    import subprocess
    import sys

    kandidaten = [os.environ.get("LANGUAGE", ""), os.environ.get("LC_ALL", ""),
                  os.environ.get("LANG", "")]
    try:
        kandidaten.append(locale.getlocale()[0] or "")
    except Exception:
        pass
    try:
        kandidaten.append(locale.getdefaultlocale()[0] or "")
    except Exception:
        pass

    # macOS: die Systemsprache steht in den Einstellungen, nicht in der Umgebung.
    if sys.platform == "darwin":
        try:
            ausgabe = subprocess.run(["defaults", "read", "-g", "AppleLanguages"],
                                     capture_output=True, text=True, timeout=5).stdout
            # Die Ausgabe ist eine Liste: ( "de-DE" ) — deshalb jedes Wort einzeln prüfen.
            for zeile in ausgabe.replace("(", " ").replace(")", " ").replace('"', " ").split(","):
                if zeile.strip():
                    kandidaten.append(zeile.strip())
        except Exception:
            pass

    for wert in kandidaten:
        text = (wert or "").lower()
        if text.startswith("de") or text.startswith("german") or text.startswith("deutsch"):
            return "de"
    return "en"


class App:
    def __init__(self, sprache: str = "de"):
        self.sprache = sprache
        self.t = TEXTE.get(sprache, TEXTE["de"])
        self.wurzel = tk.Tk()
        self.wurzel.title(self.t["titel"])
        # Keine feste Höhe: auf Windows wurden die unteren Knöpfe abgeschnitten, weil
        # Schrift und DPI anders ausfallen (24.11.2026: „Startfenster unten abgeschnitten,
        # Abbrechen halb verdeckt"). Das Fenster richtet sich nach seinem Inhalt.
        self.wurzel.minsize(440, 0)

        rahmen = ttk.Frame(self.wurzel, padding=16)
        rahmen.pack(fill="both", expand=True)
        ttk.Label(rahmen, text=self.t["titel"],
                  font=("Helvetica", 18, "bold")).pack(anchor="w")
        ttk.Label(rahmen, text=self.t.get("start_text", self.t["wort_text"]), wraplength=380,
                  foreground="#555").pack(anchor="w", pady=(4, 12))
        # Ein Knopf für alle Dateien (Julius' Wunsch 24.11.2026): Bild, PDF und Text
        # werden an der Endung erkannt. Danach die Reihenfolge wie auf Android.
        # Seit 25.11.2026 gibt es keinen eigenen Knopf „Markieren von Hand" mehr —
        # die Wahl steckt im ersten Schritt der Rückfrage (wie in der macOS-Fassung).
        ttk.Button(rahmen, text=self.t.get("k_datei", "Datei wählen"),
                   command=self.datei_waehlen).pack(fill="x", pady=4)
        ttk.Button(rahmen, text=self.t["k_ablage"],
                   command=self.ablage_waehlen).pack(fill="x", pady=4)
        self.ablage_label = ttk.Label(rahmen, text=self.ablage_text(), foreground="#555")
        self.ablage_label.pack(anchor="w", pady=(0, 8))
        ttk.Button(rahmen, text=self.t.get("k_letzte", "Letzte Dateien"),
                   command=self.letzte_zeigen).pack(fill="x", pady=4)
        ttk.Button(rahmen, text=self.t.get("k_ergebnisse", "Ergebnisse"),
                   command=self.ergebnisse_zeigen).pack(fill="x", pady=4)
        ttk.Button(rahmen, text=self.t.get("k_info", "Info"),
                   command=self.info_zeigen).pack(fill="x", pady=4)
        ttk.Button(rahmen, text=self.t["k_abbrechen"],
                   command=self.wurzel.destroy).pack(fill="x", pady=(10, 0))

        # Fenstergröße aus dem Inhalt ableiten. `geometry("")` allein genügte nicht:
        # auf Windows blieb „Abbrechen" weiter abgeschnitten (Bild vom 24.11.2026),
        # weil das Fenster seine angeforderte Größe nicht übernahm. Deshalb ausdrücklich
        # setzen — Breite aus dem Inhalt, Höhe aus dem Inhalt plus etwas Luft.
        self.wurzel.update_idletasks()
        breite = max(460, self.wurzel.winfo_reqwidth())
        hoehe = self.wurzel.winfo_reqheight() + 12
        self.wurzel.minsize(breite, hoehe)
        self.wurzel.geometry("%dx%d" % (breite, hoehe))
        # Zweite Messung kurz nach dem Anzeigen: unter Windows stehen Schriftmaße erst
        # nach dem ersten Zeichnen fest. Ohne das blieb „Abbrechen" verdeckt.
        self.wurzel.after(250, self.groesse_nachziehen)

    def info_zeigen(self) -> None:
        """Zeigt Fassung, Fähigkeiten und Grenzen — in der gewählten Sprache."""
        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t.get("k_info", "Info"))
        rahmen = ttk.Frame(fenster, padding=16)
        rahmen.pack(fill="both", expand=True)
        try:
            from . import __version__ as fassung
        except Exception:
            fassung = "?"
        ttk.Label(rahmen, text=self.t["titel"] + " · " + str(fassung),
                  font=("Helvetica", 14, "bold")).pack(anchor="w")
        text = tk.Text(rahmen, wrap="word", width=64, height=18, relief="flat")
        text.insert("1.0", self.t.get("info_text", ""))
        text.configure(state="disabled")
        text.pack(fill="both", expand=True, pady=(8, 8))
        ttk.Button(rahmen, text=self.t.get("k_fertig", "Fertig") if "k_fertig" in self.t
                   else "OK", command=fenster.destroy).pack(anchor="e")

    def groesse_nachziehen(self) -> None:
        try:
            self.wurzel.update_idletasks()
            breite = max(460, self.wurzel.winfo_reqwidth())
            hoehe = self.wurzel.winfo_reqheight() + 12
            self.wurzel.minsize(breite, hoehe)
            self.wurzel.geometry("%dx%d" % (breite, hoehe))
        except Exception:
            pass

    # ---------------------------------------------------------------- Ablage

    def ablage_text(self) -> str:
        ablage = lade_einstellungen().get("ablage")
        return ("Ablage: " + str(ablage)) if ablage else self.t["ablage_original"]

    def ablage_waehlen(self) -> None:
        ordner = filedialog.askdirectory(title=self.t["k_ablage"])
        if ordner:
            daten = lade_einstellungen()
            daten["ablage"] = ordner
            speichere_einstellungen(daten)
        self.ablage_label.config(text=self.ablage_text())

    # ---------------------------------------------------------------- Ablauf

    def datei_waehlen(self) -> None:
        datei = filedialog.askopenfilename(
            title=self.t.get("k_datei", "Datei wählen"),
            filetypes=[("Bilder, PDF und Text",
                        "*.png *.jpg *.jpeg *.tif *.tiff *.webp *.pdf *.txt *.csv *.log *.md"),
                       ("Alle Dateien", "*.*")],
        )
        if not datei:
            return
        self.datei_oeffnen(Path(datei))

    def _letzte_datei(self) -> Path:
        ordner = Path.home() / ".config" / "text-schwaerzen"
        ordner.mkdir(parents=True, exist_ok=True)
        return ordner / "letzte.json"

    def merke_datei(self, datei: Path) -> None:
        """Hält die letzten acht Dateien fest (wie der Android-Verlauf)."""
        import json
        ziel = self._letzte_datei()
        try:
            liste = json.loads(ziel.read_text(encoding="utf-8"))
        except Exception:
            liste = []
        pfad = str(datei)
        liste = [z for z in liste if z != pfad]
        liste.append(pfad)
        try:
            ziel.write_text(json.dumps(liste[-8:], ensure_ascii=False, indent=1), encoding="utf-8")
        except Exception:
            pass

    def letzte_zeigen(self) -> None:
        """Zeigt die zuletzt geöffneten Dateien; ein Klick öffnet sie."""
        import json
        try:
            liste = json.loads(self._letzte_datei().read_text(encoding="utf-8"))
        except Exception:
            liste = []
        liste = [z for z in liste if Path(z).exists()]
        if not liste:
            messagebox.showinfo(self.t.get("k_letzte", "Letzte Dateien"),
                                self.t.get("keine_letzte", "Noch keine Datei geöffnet"))
            return
        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t.get("k_letzte", "Letzte Dateien"))
        fenster.transient(self.wurzel)
        rahmen = ttk.Frame(fenster, padding=12)
        rahmen.pack(fill="both", expand=True)
        for eintrag in reversed(liste):
            pfad = Path(eintrag)

            def oeffne(p: Path = pfad) -> None:
                fenster.destroy()
                self.datei_oeffnen(p)

            zeile = ttk.Frame(rahmen)
            zeile.pack(fill="x", pady=2)
            ttk.Button(zeile, text=pfad.name, width=40,
                       command=oeffne).pack(side="left")
            ttk.Button(zeile, text=self.t.get("k_zeigen", "Im Dateimanager zeigen"),
                       command=lambda d=pfad: self.zeige_im_ordner(d)).pack(side="left", padx=2)
        ttk.Button(rahmen, text=self.t.get("k_fertig", "Fertig"),
                   command=fenster.destroy).pack(pady=(10, 0))

    def ergebnisse_zeigen(self) -> None:
        """Zeigt die geschwärzten Dateien in der Ablage und erlaubt Löschen."""
        # Ablage aus den Einstellungen; ohne Angabe liegt alles neben dem Original,
        # dann suchen wir im Bilder-Ordner des Benutzers.
        eingestellt = lade_einstellungen().get("ablage")
        if eingestellt:
            ordner = Path(str(eingestellt))
        else:
            ordner = Path.home() / "Pictures"
            if not ordner.exists():
                ordner = Path.home() / "Bilder"
            if not ordner.exists():
                ordner = Path.home() / "Desktop"
        treffer = sorted(Path(ordner).glob("*-geschwaerzt.*"), key=lambda p: p.stat().st_mtime,
                         reverse=True)[:20] if Path(ordner).exists() else []
        if not treffer:
            messagebox.showinfo(self.t.get("k_ergebnisse", "Ergebnisse"),
                                self.t.get("keine_ergebnisse",
                                           "Noch keine geschwärzte Datei in der Ablage."))
            return
        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t.get("k_ergebnisse", "Ergebnisse"))
        fenster.transient(self.wurzel)
        rahmen = ttk.Frame(fenster, padding=12)
        rahmen.pack(fill="both", expand=True)
        for datei in treffer:
            zeile = ttk.Frame(rahmen)
            zeile.pack(fill="x", pady=2)
            ttk.Label(zeile, text=datei.name, width=42).pack(side="left")
            ttk.Button(zeile, text=self.t.get("k_oeffnen", "Öffnen"),
                       command=lambda d=datei: self.oeffne_extern(d)).pack(side="left", padx=2)
            ttk.Button(zeile, text=self.t.get("k_zeigen", "Im Dateimanager zeigen"),
                       command=lambda d=datei: self.zeige_im_ordner(d)).pack(side="left", padx=2)
            ttk.Button(zeile, text=self.t.get("k_loeschen", "Löschen"),
                       command=lambda d=datei, z=zeile: self.loesche_datei(d, z)).pack(side="left")
        ttk.Button(rahmen, text=self.t.get("k_fertig", "Fertig"),
                   command=fenster.destroy).pack(pady=(10, 0))

    def zeige_im_ordner(self, datei: Path) -> None:
        """Oeffnet den Ordner mit der Datei - je System mit eigenem Befehl.

        Julius' Wunsch vom 25.11.2026; auf Android heisst es "Ordner im
        Dateimanager zeigen". macOS und Windows markieren die Datei gleich,
        Linux oeffnet nur den Ordner.
        """
        import subprocess
        import sys
        try:
            if sys.platform == "darwin":
                subprocess.Popen(["open", "-R", str(datei)])
            elif sys.platform.startswith("win"):
                subprocess.Popen(["explorer", "/select,", str(datei)])
            else:
                subprocess.Popen(["xdg-open", str(datei.parent)])
        except Exception as fehler:  # noqa: BLE001
            messagebox.showerror(self.t.get("k_zeigen", "Im Dateimanager zeigen"), str(fehler))

    def oeffne_extern(self, datei: Path) -> None:
        import subprocess
        import sys
        try:
            if sys.platform == "darwin":
                subprocess.Popen(["open", str(datei)])
            elif sys.platform.startswith("win"):
                import os
                os.startfile(str(datei))  # type: ignore[attr-defined]
            else:
                subprocess.Popen(["xdg-open", str(datei)])
        except Exception:
            messagebox.showinfo(self.t.get("k_oeffnen", "Öffnen"), str(datei))

    def loesche_datei(self, datei: Path, zeile) -> None:
        if not messagebox.askyesno(self.t.get("k_loeschen", "Löschen"),
                                   "%s\n\n%s" % (datei.name,
                                                  self.t.get("loeschen_frage",
                                                             "Endgültig entfernen?"))):
            return
        try:
            datei.unlink()
            zeile.destroy()
        except Exception as fehler:  # noqa: BLE001
            messagebox.showerror(self.t.get("k_loeschen", "Löschen"), str(fehler))

    def datei_oeffnen(self, datei: Path) -> None:
        """Entscheidet an der Endung: Textdatei oder Bild/PDF."""
        self.merke_datei(datei)
        if datei.suffix.lower() in {".txt", ".csv", ".log", ".md"}:
            self.textdatei_schwaerzen(datei)
        else:
            self.verarbeite(datei)

    def textdatei_schwaerzen(self, datei: Path) -> None:
        """Textdatei schwärzen und daneben ablegen."""
        modus = self.frage_modus_text(datei.name)
        if modus is None:
            return
        from .textdatei import schwærze_datei
        try:
            ziel, zaehler, getroffen = schwærze_datei(Path(datei), modus)
        except Exception as fehler:  # noqa: BLE001 — Meldung statt Absturz
            messagebox.showerror(self.t.get("k_text", "Textdatei schwärzen"), str(fehler))
            return
        teile = ", ".join("%s %d" % (self.t.get("art_" + art, art), zahl)
                          for art, zahl in zaehler.items()) or self.t.get("nichts", "nichts")
        messagebox.showinfo(self.t["titel"],
                            "%s\n%s\n\n%s" % (ziel.name, teile, getroffen))

    def frage_modus_text(self, dateiname: str = "") -> str | None:
        """Nur zwei Stufen — einen Rahmen und Markieren gibt es bei Text nicht."""
        return self.frage_knopf(self.t["was_schwaerzen"],
                                [("gezielt", self.t["k_gezielt"]),
                                 ("alles", self.t["k_alles"])], dateiname)

    def frage_knopf(self, titel: str, optionen: list[tuple[str, str]],
                    dateiname: str = "") -> str | None:
        """Eine Rückfrage mit Knöpfen; gibt den Wert des gedrückten Knopfes zurück.

        Eigene Knöpfe statt `messagebox`: bei vier Knöpfen ist die Antwort nicht mehr
        eindeutig (so steht es auch in der macOS-Fassung). Abbrechen gibt None.
        """
        fenster = tk.Toplevel(self.wurzel)
        fenster.title(titel)
        fenster.transient(self.wurzel)
        fenster.resizable(False, False)
        fenster.grab_set()
        wahl: list[str | None] = [None]
        ttk.Label(fenster, text=titel, font=("Helvetica", 14, "bold")).pack(padx=20, pady=(14, 2))
        if dateiname:
            # Der Name gehört dazu: sonst weiß niemand mehr, welche Datei gemeint ist,
            # wenn der Dateidialog geschlossen ist.
            ttk.Label(fenster, text=dateiname, foreground="#555",
                      wraplength=340).pack(padx=20, pady=(0, 8))

        def setze(wert: str) -> None:
            wahl[0] = wert
            fenster.destroy()

        for wert, text in optionen:
            ttk.Button(fenster, text=text, width=34,
                       command=lambda w=wert: setze(w)).pack(padx=20, pady=3)
        ttk.Button(fenster, text=self.t["k_abbrechen"], width=34,
                   command=fenster.destroy).pack(padx=20, pady=(10, 16))
        self.wurzel.wait_window(fenster)
        return wahl[0]

    def frage_modus(self, ist_pdf: bool, dateiname: str = "") -> str | None:
        """Fragt in zwei Schritten: erst das Vorgehen, dann die Feinheiten.

        Vorher standen vier Knöpfe auf einmal im Fenster („Wie stark schwärzen?").
        Julius hat am 25.11.2026 für die macOS-Fassung entschieden: zwei Schritte,
        alles so klar und einfach wie nötig. Die Tk-Fassung folgt jetzt nach.
        """
        erstes = self.frage_knopf(self.t["wie_vorgehen"],
                                  [("automatisch", self.t["k_auto"]),
                                   ("markieren", self.t["k_selbst"])],
                                  dateiname)
        if erstes is None:
            return None
        if erstes == "markieren":
            return "markieren"
        optionen = [("gezielt", self.t["k_gezielt"]), ("alles", self.t["k_alles"]),
                    ("worte", self.t["k_worte"])]
        if not ist_pdf:
            optionen.append(("bereich", self.t["k_rahmen"]))
        return self.frage_knopf(self.t["was_schwaerzen"], optionen, dateiname)

    def frage_worte(self, bildpfad: Path) -> set[str] | None:
        woerter = erkenne_worte(bildpfad)
        zaehler: dict[str, int] = {}
        for wort in woerter:
            zaehler[wort.text] = zaehler.get(wort.text, 0) + 1
        if not zaehler:
            return set()

        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t["wort_titel"])
        fenster.transient(self.wurzel)
        fenster.grab_set()
        ttk.Label(fenster, text=self.t["wort_text"], wraplength=420,
                  foreground="#555").pack(padx=16, pady=(12, 8), anchor="w")

        leinwand = tk.Canvas(fenster, width=480, height=380)
        leinwand.pack(side="left", fill="both", expand=True, padx=(16, 0))
        rollbalken = ttk.Scrollbar(fenster, orient="vertical", command=leinwand.yview)
        rollbalken.pack(side="right", fill="y", padx=(0, 16))
        leinwand.configure(yscrollcommand=rollbalken.set)
        innen = ttk.Frame(leinwand)
        leinwand.create_window((0, 0), window=innen, anchor="nw")

        kaestchen: list[tuple[tk.BooleanVar, str]] = []
        for text, anzahl in sorted(zaehler.items(), key=lambda p: (-p[1], p[0])):
            var = tk.BooleanVar(value=False)
            beschriftung = "%s  (%d×)" % (text, anzahl) if anzahl > 1 else text
            ttk.Checkbutton(innen, text=beschriftung, variable=var).pack(anchor="w")
            kaestchen.append((var, text))
        innen.update_idletasks()
        leinwand.configure(scrollregion=leinwand.bbox("all"))

        ergebnis: list[set[str] | None] = [None]

        def alle(wert: bool) -> None:
            for var, _ in kaestchen:
                var.set(wert)

        knoepfe = ttk.Frame(fenster)
        knoepfe.pack(side="bottom", fill="x", padx=16, pady=12)
        ttk.Button(knoepfe, text=self.t["k_alle"], command=lambda: alle(True)).pack(side="left")
        ttk.Button(knoepfe, text=self.t["k_keine"], command=lambda: alle(False)).pack(side="left", padx=6)

        def uebernehmen() -> None:
            gewaehlt = {text for var, text in kaestchen if var.get()}
            ergebnis[0] = gewaehlt or None
            fenster.destroy()

        ttk.Button(knoepfe, text=self.t["k_schwaerzen"], command=uebernehmen).pack(side="right")
        ttk.Button(knoepfe, text=self.t["k_abbrechen"],
                   command=fenster.destroy).pack(side="right", padx=6)
        self.wurzel.wait_window(fenster)
        return ergebnis[0]

    def frage_rahmen(self, bildpfad: Path) -> tuple[int, int, int, int] | None:
        with Image.open(bildpfad) as bild:
            anzeige = bild.convert("RGB").copy()
        breite, hoehe = anzeige.size
        faktor = min(1.0, 900 / breite, 640 / hoehe)
        klein = anzeige.resize((int(breite * faktor), int(hoehe * faktor)))

        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t["rahmen_titel"])
        fenster.transient(self.wurzel)
        fenster.grab_set()
        foto = ImageTk.PhotoImage(klein)
        leinwand = tk.Canvas(fenster, width=klein.width, height=klein.height)
        leinwand.pack()
        leinwand.create_image(0, 0, anchor="nw", image=foto)
        leinwand.image = foto  # Referenz halten, sonst verschwindet das Bild

        zustand: dict[str, float] = {}
        rechteck: list[int] = [0]

        def starten(event) -> None:
            zustand["x"] = event.x
            zustand["y"] = event.y

        def ziehen(event) -> None:
            if "x" not in zustand:
                return
            if rechteck[0]:
                leinwand.delete(rechteck[0])
            rechteck[0] = leinwand.create_rectangle(zustand["x"], zustand["y"], event.x, event.y,
                                                    outline="green", width=2)

        leinwand.bind("<ButtonPress-1>", starten)
        leinwand.bind("<B1-Motion>", ziehen)

        ergebnis: list[tuple[int, int, int, int] | None] = [None]

        def uebernehmen(_event=None) -> None:
            kasten = leinwand.coords(rechteck[0]) if rechteck[0] else None
            if not kasten:
                ergebnis[0] = None
            else:
                x1, y1, x2, y2 = kasten
                ergebnis[0] = (int(min(x1, x2) / faktor), int(min(y1, y2) / faktor),
                               int(max(x1, x2) / faktor), int(max(y1, y2) / faktor))
            fenster.destroy()

        leinwand.bind("<Return>", uebernehmen)
        ttk.Button(fenster, text=self.t["k_schwaerzen"], command=uebernehmen).pack(pady=6)
        fenster.wait_window()
        return ergebnis[0]

    def oeffne_editor(self, quelle: Path) -> None:
        """„Selbst markieren" — der Markier-Editor, mit allen PDF-Seiten.

        Fehlt PyMuPDF (nur bei PDF nötig) oder scheitert das Öffnen, erscheint eine
        Meldung statt eines Absturzes: bis 25.11.2026 wäre hier ein Traceback
        erschienen, und das Fenster hätte wortlos nichts getan.
        """
        try:
            from .editor import oeffne
            oeffne(Path(quelle), self.sprache)
        except ImportError as fehler:
            messagebox.showerror(self.t["k_selbst"],
                                 "PyMuPDF fehlt — PDFs lassen sich nicht öffnen.\n"
                                 "pip install pymupdf\n\n%s" % fehler)
        except Exception as fehler:  # noqa: BLE001 — Meldung statt Absturz
            messagebox.showerror(self.t["k_selbst"],
                                 "%s\n\n%s" % (quelle.name, fehler))

    def verarbeite(self, quelle: Path) -> None:
        ist_pdf = quelle.suffix.lower() == ".pdf"
        modus = self.frage_modus(ist_pdf, quelle.name)
        if not modus:
            return
        if modus == "markieren":
            self.oeffne_editor(quelle)
            return

        worte: set[str] = set()
        if modus == "worte":
            if ist_pdf:
                try:
                    import pymupdf as fitz
                except ImportError:
                    import fitz
                import tempfile
                with fitz.open(quelle) as dokument:
                    pixmap = dokument[0].get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
                    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
                        tmp.write(pixmap.tobytes("png"))
                        erste_seite = Path(tmp.name)
                try:
                    gewaehlt = self.frage_worte(erste_seite)
                finally:
                    erste_seite.unlink(missing_ok=True)
            else:
                gewaehlt = self.frage_worte(quelle)
            if not gewaehlt:
                self.wurzel.deiconify()
                return
            worte = gewaehlt

        behalten = None
        if modus == "bereich":
            behalten = self.frage_rahmen(quelle)
            if behalten is None:
                return

        ziel = ziel_pfad(quelle)
        if ist_pdf:
            self.zeige_fortschritt(quelle, ziel, modus, worte, behalten)
        else:
            zahlen = schwaeze_datei(quelle, ziel, modus, worte, behalten)
            messagebox.showinfo(self.t["fertig"], self.berichts_text(ziel, zahlen))

    def berichts_text(self, ziel: Path, zahlen: dict) -> str:
        if not zahlen:
            return "nichts Sensibles gefunden\n\n" + str(ziel)
        teile = ", ".join("%s %d" % (regeln.bezeichnung(art, self.sprache), n)
                          for art, n in sorted(zahlen.items()))
        return teile + "\n\n" + str(ziel)

    def zeige_fortschritt(self, quelle: Path, ziel: Path, modus: str,
                          worte: set[str], behalten) -> None:
        fenster = tk.Toplevel(self.wurzel)
        # Vorher stand hier „Wie stark schwärzen?" — die Frage ist aber längst
        # beantwortet, das Fenster zeigt den Fortschritt.
        fenster.title(self.t["titel"])
        fenster.transient(self.wurzel)
        fenster.grab_set()
        beschriftung = ttk.Label(fenster, text="…")
        beschriftung.pack(padx=20, pady=(16, 6))
        balken = ttk.Progressbar(fenster, length=360, mode="determinate")
        balken.pack(padx=20, pady=(0, 16))
        meldungen: queue.Queue = queue.Queue()

        def stand(fertig: int, gesamt: int) -> None:
            meldungen.put((fertig, gesamt))

        ergebnis: dict = {}

        def arbeit() -> None:
            ergebnis["wert"] = schwaeze_pdf(quelle, ziel, modus, worte, behalten,
                                            sprache="deu", stand=stand)

        faden = threading.Thread(target=arbeit, daemon=True)
        faden.start()

        def nachsehen() -> None:
            try:
                while True:
                    fertig, gesamt = meldungen.get_nowait()
                    balken["maximum"] = gesamt
                    balken["value"] = fertig
                    beschriftung.config(text="%s %d %s %d" % (self.t["seite"], fertig,
                                                              self.t["von"], gesamt))
            except queue.Empty:
                pass
            if faden.is_alive():
                fenster.after(120, nachsehen)
            else:
                fenster.destroy()
                seiten, zahlen = ergebnis.get("wert", (0, {}))
                messagebox.showinfo(self.t["fertig"], self.berichts_text(ziel, zahlen))

        fenster.after(120, nachsehen)
        self.wurzel.wait_window(fenster)

    def laufen(self) -> int:
        self.wurzel.mainloop()
        return 0


def laufe() -> int:
    return App(sprache_waehlen()).laufen()
