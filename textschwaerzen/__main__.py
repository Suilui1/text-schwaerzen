"""Start: ohne Argumente die Oberfläche, mit Argumenten die Befehlszeile (zum Prüfen)."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import regeln
from .masken import schwaeze_datei
from .pdf import schwaeze_pdf


def einstellungen_pfad() -> Path:
    return Path.home() / ".text-schwaerzen.json"


def lade_einstellungen() -> dict:
    try:
        return json.loads(einstellungen_pfad().read_text(encoding="utf-8"))
    except Exception:
        return {}


def speichere_einstellungen(daten: dict) -> None:
    try:
        einstellungen_pfad().write_text(json.dumps(daten, ensure_ascii=False, indent=1),
                                        encoding="utf-8")
    except Exception:
        pass


def ziel_pfad(quelle: Path, endung: str | None = None) -> Path:
    """Ergebnis neben dem Original oder im gewählten Ablageordner."""
    endung = endung or ("pdf" if quelle.suffix.lower() == ".pdf" else "png")
    name = quelle.stem + "-geschwaerzt." + endung
    ablage = lade_einstellungen().get("ablage")
    if ablage:
        ordner = Path(ablage)
        if ordner.is_dir():
            return ordner / name
    return quelle.with_name(name)


BERICHTZIEL: list[str] = []


def schreibe_bericht(zeile: str) -> None:
    print(zeile)
    if BERICHTZIEL:
        try:
            with open(BERICHTZIEL[0], "a", encoding="utf-8") as f:
                f.write(zeile + "\n")
        except Exception:
            pass


def bericht(ziel: Path, zahlen: dict, sprache: str = "de") -> None:
    if not zahlen:
        schreibe_bericht("  nichts Sensibles gefunden — Datei geschrieben: " + str(ziel))
        return
    teile = ", ".join("%s %d" % (regeln.bezeichnung(art, sprache), n) for art, n in sorted(zahlen.items()))
    schreibe_bericht("  geschwärzt: " + teile)
    schreibe_bericht("  Datei: " + str(ziel))


def befehlszeile(argumente: list[str]) -> int:
    p = argparse.ArgumentParser(prog="textschwaerzen",
                                description="Text in Bildern und PDFs schwärzen")
    p.add_argument("--bild", help="Bilddatei")
    p.add_argument("--pdf", help="PDF-Datei")
    p.add_argument("--text", help="Textdatei (.txt) schwärzen")
    p.add_argument("--modus", default="gezielt",
                   choices=["gezielt", "alles", "worte", "bereich"])
    p.add_argument("--worte", default="", help="Komma-getrennte Wörter (Modus worte)")
    p.add_argument("--behalten", default="", help="x,y,breite,hoehe (Modus bereich)")
    p.add_argument("--aus", default="", help="Zieldatei")
    p.add_argument("--markieren", action="store_true", help="Editor zum Markieren öffnen")
    p.add_argument("--bericht", default="", help="Ergebnis zusätzlich in diese Datei schreiben")
    p.add_argument("--sprache", default="deu", help="Tesseract-Sprache (Vorgabe deu)")
    a = p.parse_args(argumente)
    if a.bericht:
        # NICHT überschreiben: der Selbsttest ruft das Programm dreimal auf, und jeder Lauf
        # hätte den Bericht des vorigen gelöscht (am 24.11.2026 auf Windows passiert —
        # im Bericht stand nur noch der letzte Modus).
        BERICHTZIEL.append(a.bericht)
        try:
            with open(a.bericht, "a", encoding="utf-8") as f:
                f.write("\n=== Aufruf: %s ===\n" % " ".join(argumente))
        except Exception:
            pass

    if a.text:
        # Textdateien werden zeilenweise geschwärzt — wie in der Android-Fassung.
        from .textdatei import schwærze_datei
        quelle = Path(a.text)
        ziel = Path(a.aus) if a.aus else quelle.with_name(
            quelle.stem + "-geschwaerzt" + quelle.suffix)
        try:
            ziel, zahlen, getroffen = schwærze_datei(quelle, a.modus, ziel)
        except Exception as fehler:  # noqa: BLE001
            print("  Textdatei nicht lesbar: %s" % fehler, file=sys.stderr)
            return 1
        schreibe_bericht("Textdatei: %d Zeile(n) geschwärzt" % getroffen)
        bericht(ziel, zahlen)
        return 0

    if a.markieren and a.bild:
        from .editor import oeffne
        oeffne(Path(a.bild))
        return 0

    if not a.bild and not a.pdf:
        p.print_help()
        return 2

    worte = {w.strip() for w in a.worte.split(",") if w.strip()}
    behalten = None
    if a.behalten:
        teile = [int(z) for z in a.behalten.split(",")]
        if len(teile) != 4:
            print("  --behalten braucht x,y,breite,hoehe", file=sys.stderr)
            return 2
        behalten = tuple(teile)

    if a.pdf:
        quelle = Path(a.pdf)
        ziel = Path(a.aus) if a.aus else ziel_pfad(quelle)
        seiten, zahlen = schwaeze_pdf(quelle, ziel, a.modus, worte, behalten)
        schreibe_bericht("PDF: %d Seite(n)" % seiten)
        bericht(ziel, zahlen)
        return 0

    quelle = Path(a.bild)
    ziel = Path(a.aus) if a.aus else ziel_pfad(quelle)
    zahlen = schwaeze_datei(quelle, ziel, a.modus, worte, behalten)
    bericht(ziel, zahlen)
    return 0


def starte_oberflaeche() -> int:
    from .gui import laufe
    return laufe()


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] in ("--bild", "--pdf", "--text", "--help", "-h"):
        sys.exit(befehlszeile(sys.argv[1:]))
    sys.exit(starte_oberflaeche())
