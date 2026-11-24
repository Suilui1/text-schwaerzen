"""PDF: jede Seite rastern, schwärzen, als neues PDF schreiben.

Warum rastern (24.11.2026): Kästen über den Text zu malen sieht gut aus, aber der Text steht
weiter darunter und läßt sich markieren und kopieren. Mit PyMuPDF wird jede Seite zu einem
Bild — das Ergebnis ist nicht mehr durchsuchbar, dafür ist nichts mehr zu holen.
"""
from __future__ import annotations

from pathlib import Path

try:
    import pymupdf as fitz
except ImportError:  # ältere Fassungen
    import fitz

from .masken import schwaeze_datei


def seitenzahl(pdf: Path) -> int:
    with fitz.open(pdf) as dokument:
        return dokument.page_count


def schwaeze_pdf(quelle: Path, ziel: Path, modus: str = "gezielt",
                 worte: set[str] | None = None,
                 behalten: tuple[int, int, int, int] | None = None,
                 sprache: str = "de", skalierung: float = 2.0,
                 stand=None) -> tuple[int, dict[str, int]]:
    """Schwärzt jede Seite und schreibt ein neues PDF. `stand(fertig, gesamt)` meldet den Stand."""
    import tempfile

    gesamt: dict[str, int] = {}
    with fitz.open(quelle) as alt, fitz.open() as neu:
        for nummer, seite in enumerate(alt, start=1):
            if stand:
                stand(nummer, alt.page_count)
            kasten = seite.rect
            pixmap = seite.get_pixmap(matrix=fitz.Matrix(skalierung, skalierung), alpha=False)
            with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
                zwischen = Path(tmp.name)
            try:
                pixmap.save(zwischen)
                bearbeitet = zwischen.with_name(zwischen.stem + "-geschwaerzt.png")
                zahlen = schwaeze_datei(zwischen, bearbeitet, modus, worte, behalten, sprache)
                for art, anzahl in zahlen.items():
                    gesamt[art] = gesamt.get(art, 0) + anzahl
                neue_seite = neu.new_page(width=kasten.width, height=kasten.height)
                neue_seite.insert_image(kasten, filename=str(bearbeitet))
                bearbeitet.unlink(missing_ok=True)
            finally:
                zwischen.unlink(missing_ok=True)
        neu.save(ziel)
        return nummer, gesamt
