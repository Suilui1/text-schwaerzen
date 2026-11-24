"""Texterkennung mit Tesseract — Wort für Wort, mit Lage im Bild.

Warum Tesseract (24.11.2026): Die macOS-Fassung nutzt Apples Vision. Das gibt es nur auf
Apple-Rechnern. Für Windows und Linux wurde gemessen: Tesseract 5.5.4 mit der deutschen
Sprachdatei las 24 von 24 Umlautwörtern richtig (Ärger, Änderung, Öffnung, Größe, Straße,
Fußgänger, süß, natürlich …) und liefert über die TSV-Ausgabe die Lage jedes Wortes.
"""
from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

from PIL import Image


@dataclass
class Wort:
    text: str
    links: int
    oben: int
    breite: int
    hoehe: int
    sicherheit: float = 0.0

    @property
    def rechts(self) -> int:
        return self.links + self.breite

    @property
    def unten(self) -> int:
        return self.oben + self.hoehe


class TesseractFehlt(RuntimeError):
    pass


def tesseract_pfad() -> str:
    """Erst neben dem Programm suchen (mitgeliefert), dann im System.

    Beim gepackten Programm liegt Tesseract im Ordner `tesseract` daneben — dann muß der
    Nutzer nichts installieren. Auf Linux ist es meist schon im System (apt).
    """
    import os
    import sys

    if getattr(sys, "frozen", False):
        basis = Path(sys.executable).parent
    else:
        basis = Path(__file__).resolve().parent.parent
    kandidaten = [basis / "tesseract" / "tesseract.exe", basis / "tesseract" / "tesseract",
                  basis / "tesseract" / "bin" / "tesseract.exe"]
    for kandidat in kandidaten:
        if kandidat.exists():
            tessdata = kandidat.parent / "tessdata"
            if not tessdata.exists():
                tessdata = basis / "tessdata"
            if tessdata.exists():
                os.environ.setdefault("TESSDATA_PREFIX", str(tessdata))
            return str(kandidat)

    pfad = shutil.which("tesseract")
    if not pfad:
        raise TesseractFehlt(
            "Tesseract wurde nicht gefunden. Installation:\n"
            "  macOS:   brew install tesseract\n"
            "  Windows: https://github.com/UB-Mannheim/tesseract/wiki\n"
            "  Linux:   apt install tesseract-ocr tesseract-ocr-deu"
        )
    return pfad


def erkenne_worte(bildpfad: Path | str, sprache: str = "deu", psm: int = 3) -> list[Wort]:
    """Erkennt alle Wörter eines Bildes samt Lage (Pixel, Ursprung links oben)."""
    bildpfad = Path(bildpfad)
    lauf = subprocess.run(
        [tesseract_pfad(), str(bildpfad), "stdout", "-l", sprache, "--psm", str(psm), "tsv"],
        capture_output=True,
    )
    if lauf.returncode != 0:
        return []
    # Tesseract schreibt bei Fehlern auch Bilddaten nach stderr — deshalb Bytes lesen und
    # mit errors="replace" entschlüsseln (am 24.11.2026 gab es hier einen UnicodeDecodeError).
    ausgabe = lauf.stdout.decode("utf-8", errors="replace")
    woerter: list[Wort] = []
    for zeile in ausgabe.splitlines()[1:]:
        felder = zeile.split("\t")
        if len(felder) < 12:
            continue
        try:
            ebene = int(felder[0])
            if ebene != 5:            # 5 = Wortebene
                continue
            text = felder[11].strip()
            if not text:
                continue
            woerter.append(Wort(
                text=text,
                links=int(felder[6]), oben=int(felder[7]),
                breite=int(felder[8]), hoehe=int(felder[9]),
                sicherheit=float(felder[10]),
            ))
        except (ValueError, IndexError):
            continue
    return woerter


def erkenne_zeilen(bildpfad: Path | str, sprache: str = "deu") -> list[Wort]:
    """Erkennt Textzeilen statt Wörter — nötig für die Regeln (Mail, Name, Code …)."""
    bildpfad = Path(bildpfad)
    lauf = subprocess.run(
        [tesseract_pfad(), str(bildpfad), "stdout", "-l", sprache, "--psm", "3", "tsv"],
        capture_output=True,
    )
    if lauf.returncode != 0:
        return []
    zeilen: dict[tuple[int, int, int], list[Wort]] = {}
    for zeile in lauf.stdout.decode("utf-8", errors="replace").splitlines()[1:]:
        felder = zeile.split("\t")
        if len(felder) < 12:
            continue
        try:
            ebene = int(felder[0])
            text = felder[11].strip()
            if not text:
                continue
            schluessel = (int(felder[2]), int(felder[3]), int(felder[4]))
            zeilen.setdefault(schluessel, []).append(Wort(
                text=text, links=int(felder[6]), oben=int(felder[7]),
                breite=int(felder[8]), hoehe=int(felder[9]),
                sicherheit=float(felder[10]) if felder[10] not in ("-1", "") else 0.0,
            ))
        except (ValueError, IndexError):
            continue
    ergebnis: list[Wort] = []
    for teile in zeilen.values():
        teile.sort(key=lambda w: w.links)
        links = min(w.links for w in teile)
        oben = min(w.oben for w in teile)
        rechts = max(w.rechts for w in teile)
        unten = max(w.unten for w in teile)
        ergebnis.append(Wort(
            text=" ".join(w.text for w in teile),
            links=links, oben=oben, breite=rechts - links, hoehe=unten - oben,
            sicherheit=sum(w.sicherheit for w in teile) / len(teile),
        ))
    ergebnis.sort(key=lambda w: (w.oben, w.links))
    return ergebnis


def bildgroesse(bildpfad: Path | str) -> tuple[int, int]:
    with Image.open(bildpfad) as bild:
        return bild.size
