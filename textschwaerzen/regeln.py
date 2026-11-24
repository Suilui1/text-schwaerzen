"""Die Regeln: was wird geschwärzt?

Übernommen aus der macOS-Fassung (werkzeuge/BildSchwaerzenApp/main.swift), damit beide
Systeme dieselben Dinge erwischen. Gemessen am 23./24.11.2026:
  · Postfach-Liste (55 Zeilen): gezielt reichte NICHT — Firmennamen als Absender,
    Betreffzeilen und Codes blieben stehen. Deshalb gibt es „alles".
  · Umlaute: Tesseract deu las 24 von 24 Wörtern richtig.
"""
from __future__ import annotations

import re

MAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
IBAN = re.compile(r"\b[A-Z]{2}[0-9]{2}(?:[ ]?[0-9]{4}){3,7}[0-9]{0,2}\b")
TELEFON = re.compile(r"\+?[0-9][0-9 /\-().]{6,}[0-9]")
SCHLUESSEL = re.compile(
    r"\b(?:ghp_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,}|AKIA[A-Z0-9]{12,}"
    r"|AIza[A-Za-z0-9_-]{20,}|[A-Za-z0-9]{44,})\b"
)
KOPF = re.compile(
    r"^(?:von|an|betreff|cc|bcc|from|to|subject|datum|date|gesendet|sent)\s*[:：]",
    re.IGNORECASE,
)
GRUSS = re.compile(
    r"^(?:mit freundlichen|freundliche|viele|beste|herzliche|kind regards"
    r"|best regards|regards|cheers)",
    re.IGNORECASE,
)
# Begriffe mit Ziffern: Codes, Kundennummern, Rechnungsnummern — genau die Zeilen, die im
# Postfach standen und durch das Raster fielen (23.11.2026).
BEGRIFF = re.compile(
    r"(?i)(login[- ]?code|best[aä]tigungscode|code|kundennummer|kundennr|bestellnummer"
    r"|rechnungsnummer|auftragsnummer|vertragsnummer|pin|tan|passwort|kennwort"
    r"|zugangsnummer)"
)
NUR_ZAHLEN = re.compile(r"^[0-9][0-9 .\-/]{4,}[0-9]$")

# Adressen (24.11.2026): Auf einer Fahrkarte blieben „Bismarckallee 3" und
# „79093 Freiburg im Breisgau" stehen — Straße und Postleitzahl fehlten in den Regeln.
STRASSE = re.compile(
    r"\b[A-Za-zÄÖÜäöüß\-]{3,}(?:straße|strasse|str\.|weg|gasse|platz|allee|ring|damm"
    r"|ufer|steig|graben|hof|park|chaussee)\s*\d+[a-zA-Z]?\b",
    re.IGNORECASE,
)
PLZ_ORT = re.compile(r"\b\d{5}\s+[A-ZÄÖÜ][a-zäöüß\-]+(?:\s+[A-Za-zÄÖÜäöüß\-]+)*\b")
# Buchungs- und Bestellnummern, Fahrkarten
# Romanische Sprachen (24.11.2026): Calle, rue, Via, Rua … — die deutschen Endungen
# griffen dort nicht, erkannt wurde nur die Postleitzahl.
STRASSE_ROMANISCH = re.compile(
    r"\b(?:calle|avenida|avda|plaza|paseo|camino|carrer|rue|avenue|boulevard|place"
    r"|chemin|impasse|via|viale|piazza|corso|largo|rua|praça|praca|travessa|strada"
    r"|bulevardul|calea|str\.)\s+[A-Za-zÀ-ÿ'.\- ]{2,}\s*\d+[A-Za-z]?\b",
    re.IGNORECASE,
)
# Französisch stellt die Hausnummer voran: „12 rue de Rivoli".
STRASSE_ZAHL_VORN = re.compile(
    r"\b\d{1,4}[a-zA-Z]?\s+(?:rue|avenue|boulevard|place|chemin|impasse|quai|allée)\b",
    re.IGNORECASE,
)
# Englisch (Julius' Wahl 3, 24.11.2026): „12 Baker Street", „Baker Street 12".
STRASSE_ENGLISCH = re.compile(
    r"\b(?:\d{1,4}[a-zA-Z]?\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\s+"
    r"(?:Street|Road|Lane|Avenue|Close|Drive|Way|Place|Square|Court|Terrace"
    r"|Gardens|Hill|Park|Crescent|Grove|Mews)\b"
    r"|[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\s+"
    r"(?:Street|Road|Lane|Avenue|Close|Drive|Way|Place|Square|Court|Terrace"
    r"|Gardens|Hill|Park|Crescent|Grove|Mews)\s*\d{1,4}[a-zA-Z]?\b)"
)
# Britische Postleitzahl: SW1A 1AA, M1 1AE, B33 8TH
PLZ_ENGLISCH = re.compile(r"\b[A-Z]{1,2}\d[A-Z\d]?\s*\d[A-Z]{2}\b")
PLZ_ROMANISCH = re.compile(
    r"\b(?:\d{4}-\d{3}|\d{5,6})\s+[A-ZÀ-Þ][\wÀ-ÿ'\-]{2,}"
)
BUCHUNG = re.compile(
    r"(?i)(buchungsnummer|buchungscode|buchungsbest|bestellnummer|auftragsnummer|"
    r"fahrkarte|ticketnummer|referenznummer|belegnummer)\s*[:#]?\s*[A-Za-z0-9#\-]+"
)


# Kleingeschriebene Namen (24.11.2026): auf der Fahrkarte stand „julius krause" —
# klein, damit fiel es durch die Namensregel. Erkannt werden 2–3 Wörter aus lauter
# Buchstaben, ohne Ziffern, und ohne die häufigsten deutschen Funktionswörter.
FUNKTIONSWOERTER = {
    "der", "die", "das", "den", "dem", "des", "und", "oder", "mit", "von", "für", "fur",
    "auf", "aus", "bei", "nach", "über", "uber", "unter", "vor", "zu", "zum", "zur",
    "ist", "sind", "wird", "werden", "hat", "haben", "nicht", "auch", "sich", "sie",
    "wir", "ich", "bitte", "dort", "hier", "dann", "noch", "nur", "sowie", "alle",
    "diese", "dieser", "dieses", "eine", "einen", "einem", "einer", "eines", "ein",
}


def sieht_nach_kleinem_namen_aus(text: str) -> bool:
    roh = text.strip()
    if not roh or any(zeichen.isdigit() for zeichen in roh):
        return False
    if any(zeichen in roh for zeichen in "@:€#%/\\-"):
        return False
    teile = [w for w in roh.split() if w]
    if not 2 <= len(teile) <= 3:
        return False
    for wort in teile:
        if len(wort) < 3 or not wort.isalpha() or not wort.islower():
            return False
        if wort in FUNKTIONSWOERTER:
            return False
    return True


# Namen können mitten in einer Zeile stehen: Tesseract verschmilzt auf der Fahrkarte
# „AB 14:00" und „julius krause" zu einer Zeile. Deshalb wird nicht nur die ganze Zeile
# geprüft, sondern auch nach zwei bis drei kleingeschriebenen Wörtern gesucht.
KLEINER_NAME = re.compile(r"\b[a-zäöüß]{3,}\s+[a-zäöüß]{3,}(?:\s+[a-zäöüß]{3,})?\b")


def enthaelt_kleinen_namen(text: str) -> bool:
    for treffer in KLEINER_NAME.finditer(text):
        woerter = treffer.group(0).split()
        if all(w not in FUNKTIONSWOERTER for w in woerter):
            return True
    return False


def sieht_nach_name_aus(text: str) -> bool:
    """Personenartige Namen: 2–5 großgeschriebene Wörter, keine Ziffern, kein Doppelpunkt."""
    roh = text.strip()
    if not roh or "@" in roh or ":" in roh or "：" in roh:
        return False
    if any(zeichen.isdigit() for zeichen in roh):
        return False
    woerter = roh.split()
    if not 2 <= len(woerter) <= 5:
        return False
    return all(wort[:1].isupper() for wort in woerter)


def treffer(text: str, modus: str) -> list[str]:
    """Welche Arten von Geheimnis stecken in dieser Zeile? Leer = nicht schwärzen."""
    if modus == "alles":
        return ["alles"]
    if modus == "bereich":
        return ["ausserhalb"]
    if modus != "gezielt":
        return []
    arten: list[str] = []
    if MAIL.search(text):
        arten.append("mail")
    if IBAN.search(text):
        arten.append("iban")
    if TELEFON.search(text):
        arten.append("telefon")
    if SCHLUESSEL.search(text):
        arten.append("schluessel")
    if KOPF.search(text):
        arten.append("kopf")
    if GRUSS.search(text):
        arten.append("gruss")
    if BEGRIFF.search(text):
        arten.append("code")
    if (STRASSE.search(text) or STRASSE_ROMANISCH.search(text)
            or STRASSE_ZAHL_VORN.search(text) or STRASSE_ENGLISCH.search(text)):
        arten.append("strasse")
    if PLZ_ROMANISCH.search(text) or PLZ_ENGLISCH.search(text):
        arten.append("plz")
    if PLZ_ORT.search(text):
        arten.append("plz")
    if BUCHUNG.search(text):
        arten.append("buchung")
    if NUR_ZAHLEN.match(text) and sum(zeichen.isdigit() for zeichen in text) >= 5:
        arten.append("nummer")
    if not arten and (sieht_nach_name_aus(text) or sieht_nach_kleinem_namen_aus(text)
                      or enthaelt_kleinen_namen(text)):
        arten.append("name")
    # Jede Art nur einmal nennen (sonst steht „PLZ" zweimal im Bericht).
    eindeutig = []
    for art in arten:
        if art not in eindeutig:
            eindeutig.append(art)
    return eindeutig


BEZEICHNUNGEN = {
    "de": {"mail": "E-Mail", "iban": "IBAN", "telefon": "Telefon", "schluessel": "Schlüssel",
           "kopf": "Kopfzeile", "gruss": "Grußzeile", "name": "Name", "code": "Code oder Nummer",
           "nummer": "Nummer", "alles": "alles", "ausserhalb": "außerhalb", "wort": "Wort",
           "strasse": "Straße", "plz": "Postleitzahl", "buchung": "Buchungsnummer"},
    "en": {"mail": "e-mail", "iban": "IBAN", "telefon": "phone", "schluessel": "key",
           "kopf": "header", "gruss": "sign-off", "name": "name", "code": "code or number",
           "nummer": "number", "alles": "everything", "ausserhalb": "outside", "wort": "word",
           "strasse": "street", "plz": "postcode", "buchung": "booking number"},
}


def bezeichnung(schluessel: str, sprache: str = "de") -> str:
    return BEZEICHNUNGEN.get(sprache, BEZEICHNUNGEN["de"]).get(schluessel, schluessel)
