"""Textdateien (.txt) schwärzen.

Julius' Beobachtung am 24.11.2026: die Android-Fassung konnte Textdateien öffnen,
die Rechner-Fassungen nicht. Diese Datei schließt die Lücke für alle Systeme.

Geschwärzt wird zeilenweise: trifft eine Regel, wird der Inhalt der Zeile durch
Balken gleicher Länge ersetzt. Der Umriß bleibt, der Inhalt ist weg.
"""
from __future__ import annotations

from pathlib import Path

from .regeln import treffer

BALKEN = "\u2588"


def lies(datei: Path) -> str:
    """Liest die Datei; UTF-8 zuerst, danach Windows-1252 als Rückfall."""
    roh = datei.read_bytes()
    try:
        text = roh.decode("utf-8")
    except UnicodeDecodeError:
        text = roh.decode("cp1252", errors="replace")
    # Windows-Dateien haben CRLF; ohne Vereinheitlichung blieb ein \r am Zeilenende
    # stehen und die Ausgabe hatte gemischte Zeilenenden (25.11.2026 auf dem Stick gesehen).
    return text.replace("\r\n", "\n").replace("\r", "\n")


def schwærze_text(inhalt: str, modus: str = "gezielt") -> tuple[str, dict[str, int], int]:
    """Gibt (neuer Text, Treffer je Art, Zahl der geschwärzten Zeilen) zurück."""
    zaehler: dict[str, int] = {}
    getroffen = 0
    zeilen: list[str] = []
    for zeile in inhalt.split("\n"):
        if not zeile.strip():
            zeilen.append(zeile)
            continue
        arten = treffer(zeile, modus)
        if not arten:
            zeilen.append(zeile)
            continue
        laenge = max(3, min(500, len(zeile.strip())))
        zeilen.append(BALKEN * laenge)
        for art in arten:
            zaehler[art] = zaehler.get(art, 0) + 1
        getroffen += 1
    return "\n".join(zeilen), zaehler, getroffen


def schwærze_datei(datei: Path, modus: str = "gezielt", ziel: Path | None = None
                   ) -> tuple[Path, dict[str, int], int]:
    """Schwärzt eine Datei und schreibt das Ergebnis daneben."""
    inhalt = lies(datei)
    neu, zaehler, getroffen = schwærze_text(inhalt, modus)
    if ziel is None:
        from . import namen
        ziel = datei.with_name(datei.stem + namen.zusatz() + datei.suffix)
    ziel.write_text(neu, encoding="utf-8")
    return ziel, zaehler, getroffen
