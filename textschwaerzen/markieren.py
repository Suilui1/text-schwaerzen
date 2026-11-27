"""Markieren von Hand: Kästen, Text, Markieren, Unter-/Durchstreichen, Pfeile, Ellipsen,
Stempel und Verwischen — das, was ein Markier-Programm wie Whiteout kann.

Warum getrennt von der Oberfläche: die Zeichenfunktion läßt sich ohne Fenster prüfen.
Ein Kasten, der im Fenster richtig aussieht, aber im gespeicherten Bild woanders liegt,
wäre der schlimmste Fehler — also wird sie kopflos getestet.

Alle Maße sind in BILD-PIXELN, Ursprung links oben. Das Fenster rechnet nur zum Anzeigen um.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

SCHWARZ = (0, 0, 0)
GELB = (255, 235, 0)
ROT = (200, 30, 30)
BLAU = (30, 90, 200)

STILE = {
    "weich": "Weichzeichnen",
    "mosaik": "Mosaik",
    "pixel": "Verpixeln",
}

STEMPEL = ["GEPRÜFT", "VERTRAULICH", "KOPIE", "ENTWURF", "UNGÜLTIG", "BEZAHLT", "ERLEDIGT"]

ARTEN = {
    "kasten": "Kasten (schwarz)",
    "markieren": "Markieren (gelb)",
    "unterstreichen": "Unterstreichen",
    "durchstreichen": "Durchstreichen",
    "linie": "Linie",
    "pfeil": "Pfeil",
    "ellipse": "Ellipse",
    "text": "Text",
    "stempel": "Stempel",
    "verwischen": "Verwischen",
}


@dataclass
class Form:
    """Ein Hingemaltes."""
    art: str
    x1: int
    y1: int
    x2: int
    y2: int
    text: str = ""
    farbe: tuple[int, int, int] = SCHWARZ
    dicke: int = 4
    feld: dict = field(default_factory=dict)


def _schrift(groesse: int):
    for pfad in ("/System/Library/Fonts/Supplemental/Arial.ttf",
                 "/Library/Fonts/Arial.ttf",
                 "C:/Windows/Fonts/arial.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(pfad, groesse)
        except Exception:
            continue
    return ImageFont.load_default()


def _pfeilspitze(zeichner: ImageDraw.ImageDraw, form: Form) -> None:
    import math
    winkel = math.atan2(form.y2 - form.y1, form.x2 - form.x1)
    laenge = max(8, form.dicke * 4)
    for versatz in (0.45, -0.45):
        x = form.x2 - laenge * math.cos(winkel - versatz)
        y = form.y2 - laenge * math.sin(winkel - versatz)
        zeichner.line((form.x2, form.y2, x, y), fill=form.farbe + (255,), width=max(1, form.dicke))


def _verwischen(bild: Image.Image, form: Form) -> Image.Image:
    """Bereich unkenntlich machen — nach Stil: weichzeichnen, Mosaik oder verpixeln.

    Alle drei löschen die Lesbarkeit; unterschiedlich ist nur das Aussehen. Am 24.11.2026
    auf Wunsch ergänzt (Whiteout bietet die Wahl ebenfalls).
    """
    links, rechts = sorted((form.x1, form.x2))
    oben, unten = sorted((form.y1, form.y2))
    kasten = (max(0, links), max(0, oben), min(bild.width, rechts), min(bild.height, unten))
    if kasten[2] <= kasten[0] or kasten[3] <= kasten[1]:
        return bild
    stil = form.feld.get("stil", "weich")
    staerke = max(2, int(form.feld.get("staerke", 12)))
    ausschnitt = bild.crop(kasten)

    if stil == "mosaik":
        bloecke = max(2, staerke * 2)
        klein = ausschnitt.resize((max(1, ausschnitt.width // bloecke),
                                   max(1, ausschnitt.height // bloecke)), Image.BILINEAR)
        fertig = klein.resize(ausschnitt.size, Image.NEAREST)
    elif stil == "pixel":
        klein = ausschnitt.resize((max(1, ausschnitt.width // staerke),
                                   max(1, ausschnitt.height // staerke)), Image.NEAREST)
        fertig = klein.resize(ausschnitt.size, Image.NEAREST)
    else:  # weich
        fertig = ausschnitt.filter(ImageFilter.GaussianBlur(staerke))

    kopie = bild.copy()
    kopie.paste(fertig, kasten[:2])
    return kopie


def zeichne_formen(bild: Image.Image, formen: list[Form]) -> Image.Image:
    """Malt alle Formen der Reihe nach auf eine Kopie des Bildes."""
    grund = bild.convert("RGBA")
    for form in formen:
        if form.art == "verwischen":
            grund = _verwischen(grund.convert("RGB"), form).convert("RGBA")
            continue
        ebene = Image.new("RGBA", grund.size, (0, 0, 0, 0))
        zeichner = ImageDraw.Draw(ebene)
        links, rechts = sorted((form.x1, form.x2))
        oben, unten = sorted((form.y1, form.y2))
        farbe = form.farbe + (255,)

        if form.art == "kasten":
            zeichner.rectangle((links, oben, rechts, unten), fill=farbe)
        elif form.art == "markieren":
            zeichner.rectangle((links, oben, rechts, unten), fill=form.farbe + (110,))
        elif form.art == "linie":
            zeichner.line((form.x1, form.y1, form.x2, form.y2), fill=farbe, width=max(1, form.dicke))
        elif form.art == "unterstreichen":
            zeichner.line((links, unten, rechts, unten), fill=farbe, width=max(2, form.dicke))
        elif form.art == "durchstreichen":
            mitte = (oben + unten) // 2
            zeichner.line((links, mitte, rechts, mitte), fill=farbe, width=max(2, form.dicke))
        elif form.art == "pfeil":
            zeichner.line((form.x1, form.y1, form.x2, form.y2), fill=farbe, width=max(1, form.dicke))
            _pfeilspitze(zeichner, form)
        elif form.art == "ellipse":
            zeichner.ellipse((links, oben, rechts, unten), outline=farbe, width=max(2, form.dicke))
        elif form.art in ("text", "stempel"):
            hoehe = max(10, int(form.feld.get("groesse", form.dicke * 6)))
            schrift = _schrift(hoehe)
            if form.art == "stempel":
                zeichner.rectangle((links, oben, rechts, unten), fill=form.farbe + (235,))
                zeichner.text((links + 8, oben + 6), form.text, fill=(255, 255, 255, 255), font=schrift)
            else:
                zeichner.text((links, oben), form.text, fill=farbe, font=schrift)
        grund = Image.alpha_composite(grund, ebene)
    return grund.convert("RGB")


def speichere(bild: Image.Image, ziel: Path) -> Path:
    """Speichert im Format der Endung: PNG, JPEG oder PDF.

    Anlaß (27.11.2026): Julius — „bearbeitete bilder und texte, formate beim speichern
    anbieten". PNG für Screenshots (scharf, verlustfrei), JPEG für Fotos (klein), PDF
    zum Weitergeben (eine Bildseite, nicht durchsuchbar).
    """
    endung = ziel.suffix.lower()
    if endung == ".pdf":
        bild.convert("RGB").save(ziel, "PDF", resolution=150.0)
    elif endung in (".jpg", ".jpeg"):
        # Hohe Qualität: die Schwärzung soll scharf bleiben, gespart wird am Foto.
        bild.convert("RGB").save(ziel, "JPEG", quality=92, optimize=True)
    elif endung == ".png":
        bild.save(ziel, "PNG", optimize=True)
    else:
        raise ValueError("Unbekanntes Format: " + (endung or "(ohne Endung)"))
    return ziel


def baue_pdf(seiten: list[Image.Image], formen: list[list[Form]],
             masse: list[tuple[float, float]], ziel: Path) -> Path:
    """Mehrseitiges PDF aus gerasterten Seiten — jede Seite mit ihren Markierungen.

    Wird ausgelagert, damit der Zusammenbau ohne Fenster geprüft werden kann: bei PDFs ist
    das der Schritt, bei dem Seiten verrutschen oder verlorengehen können.
    """
    try:
        import pymupdf as fitz
    except ImportError:
        import fitz

    neu = fitz.open()
    for nummer, bild in enumerate(seiten):
        fertig = zeichne_formen(bild, formen[nummer])
        zwischen = ziel.with_name(ziel.stem + "-seite%d.png" % (nummer + 1))
        fertig.save(zwischen)
        breite, hoehe = masse[nummer] if masse[nummer][0] else (fertig.width, fertig.height)
        seite = neu.new_page(width=breite, height=hoehe)
        seite.insert_image(seite.rect, filename=str(zwischen))
        zwischen.unlink(missing_ok=True)
    neu.save(ziel)
    neu.close()
    return ziel
