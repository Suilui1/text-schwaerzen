"""Kästen füllen — der eigentliche Schwärzvorgang.

Alle Kästen sind in Pixeln, Ursprung links oben (so liefert es Tesseract, so zeichnet Pillow) —
anders als bei Apples Vision gibt es hier also keine Umrechnung, die schiefgehen kann.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

from .erkennung import Wort, erkenne_worte, erkenne_zeilen
from . import regeln


def polstern(wort: Wort) -> tuple[int, int, int, int]:
    """Kasten etwas größer: Tesseract liefert die Zeilenhöhe knapp, Ober- und Unterlängen
    blieben sonst sichtbar (an der Mac-Fassung gemessen: Balken 10 px bei 16 px Text)."""
    seitlich = max(2, int(wort.hoehe * 0.15))
    oben = max(2, int(wort.hoehe * 0.35))
    unten = max(3, int(wort.hoehe * 0.55))
    return (wort.links - seitlich, wort.oben - oben,
            wort.rechts + seitlich, wort.unten + unten)


def schwaeze_datei(quelle: Path, ziel: Path, modus: str = "gezielt",
                   worte: set[str] | None = None,
                   behalten: tuple[int, int, int, int] | None = None,
                   sprache: str = "de") -> dict[str, int]:
    """Schwärzt eine Bilddatei und schreibt sie nach `ziel`. Gibt die Zahlen zurück."""
    worte = worte or set()
    with Image.open(quelle) as bild:
        bild = bild.convert("RGB")
        zeichner = ImageDraw.Draw(bild)
        zaehler: dict[str, int] = {}

        if modus == "worte":
            for wort in erkenne_worte(quelle, sprache="deu"):
                if wort.text not in worte:
                    continue
                zeichner.rectangle(polstern(wort), fill="black")
                zaehler["wort"] = zaehler.get("wort", 0) + 1
        else:
            for zeile in erkenne_zeilen(quelle, sprache="deu"):
                if behalten is not None:
                    links, oben, rechts, unten = behalten
                    drin = (zeile.links >= links and zeile.oben >= oben
                            and zeile.rechts <= rechts and zeile.unten <= unten)
                    arten = [] if drin else ["ausserhalb"]
                else:
                    arten = regeln.treffer(zeile.text, modus)
                if not arten:
                    continue
                zeichner.rectangle(polstern(zeile), fill="black")
                for art in arten:
                    zaehler[art] = zaehler.get(art, 0) + 1

        bild.save(ziel)
    return zaehler
