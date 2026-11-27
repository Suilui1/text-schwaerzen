"""Namen für bearbeitete Dateien — ein Zusatz je Sprache.

Anlaß (27.11.2026): Julius — „die benennung bearbeiteter dateien sollte xxxx-bearbeitet
sein, in allen gewählten sprachen analog". Vorher hieß es `-geschwaerzt` (Regeln, Wörter,
Rahmen) oder `-markiert` (Editor) — zwei Namen für dieselbe Sache, und im englischen
Programm stand der deutsche Zusatz.
"""
from __future__ import annotations

ZUSATZ = {
    "de": "-bearbeitet",
    "en": "-edited",
}


def zusatz(sprache: str = "de") -> str:
    """Der Zusatz für den Dateinamen, z. B. „-bearbeitet" oder „-edited"."""
    return ZUSATZ.get(sprache, ZUSATZ["de"])


def ist_bearbeitet(name: str) -> bool:
    """Ist das eine von uns bearbeitete Datei?

    Nimmt die neuen Zusätze UND die alten (`-geschwaerzt`, `-markiert`): Dateien, die vor
    der Umbenennung entstanden sind, bleiben Ergebnisse und dürfen nicht zu Originalen
    erklärt werden.
    """
    klein = name.lower()
    return any(z in klein for z in ("-bearbeitet", "-edited", "-geschwaerzt", "-markiert"))
