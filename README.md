# Text schwärzen

Schwärzt Text in Bildern und PDFs — auf macOS, Windows und Linux.

Der Name sagt, was es tut: geschwärzt wird der **Text im Bild**, nicht das Bild.

**Vorab-Fassung zum Ausprobieren:** [Text schwärzen 2.9.0-alpha.11](https://github.com/Suilui1/text-schwaerzen/releases/tag/v2.9.0-alpha.11)
— Android (.apk), Windows (.zip), Linux (.tar.gz) und die Rechner-Fassung für macOS (.zip).
Die Alpha steht nicht im Play Store; Android fragt deshalb einmal nach der Erlaubnis,
Apps aus dieser Quelle zu installieren. Die macOS-App mit den Markier-Werkzeugen ist
nicht im Paket enthalten.

## Was es kann

- **Automatisch gezielt:** Adressen, Telefonnummern, IBANs, Schlüssel, Kopfzeilen,
  Grußzeilen, Namen, Codes und Kundennummern
- **Alles:** jede erkannte Textzeile
- **Wortauswahl:** die erkannten Wörter als Liste mit Kästchen, angekreuzte Wörter werden
  geschwärzt — im Bild an genau ihrer Stelle, im PDF auf allen Seiten
- **Rahmen:** ein Bereich bleibt lesbar, alles andere wird geschwärzt (nur Bilder)
- **PDF:** jede Seite wird als Bild gerendert, geschwärzt und neu geschrieben — der Text
  ist danach **nicht mehr markierbar oder kopierbar**
- **Ablage wählbar**, Fortschrittsbalken bei vielen Seiten

## Warum das bei PDF sicher ist

Kästen über den Text zu malen sieht gut aus, aber der Text steht weiter darunter und läßt
sich markieren und kopieren. Diese App schreibt jede Seite als Bild neu. Das Ergebnis ist
nicht mehr durchsuchbar — das ist der Preis für Sicherheit, und es ist Absicht.

## Aufbau

| Datei | Zweck |
|---|---|
| `textschwaerzen/erkennung.py` | Texterkennung mit Tesseract, Wort für Wort mit Lage |
| `textschwaerzen/regeln.py` | was geschwärzt wird (Adressen, Namen, Codes …) |
| `textschwaerzen/masken.py` | die Kästen füllen |
| `textschwaerzen/pdf.py` | PDF-Seiten rastern und neu schreiben |
| `textschwaerzen/gui.py` | Fenster: Auswahl, Wortliste, Rahmen, Fortschritt |
| `textschwaerzen/__main__.py` | Start und Befehlszeile |

## Abhängigkeiten

```
pip install pillow pymupdf pytesseract
```

Dazu **Tesseract** mit deutscher Sprachdatei:

- macOS: `brew install tesseract` und `deu.traineddata` nach `$(brew --prefix)/share/tessdata/`
- Windows: Tesseract-Installation (UB Mannheim), `deu.traineddata` ins `tessdata`-Verzeichnis
- Linux: `apt install tesseract-ocr tesseract-ocr-deu`

## Befehlszeile (zum Prüfen)

```
python -m textschwaerzen --bild foto.png --modus gezielt
python -m textschwaerzen --bild foto.png --modus worte --worte "Krause,921525957"
python -m textschwaerzen --pdf vertrag.pdf --modus worte --worte "Krause"
python -m textschwaerzen --bild foto.png --modus bereich --behalten 100,100,800,500
```

Ergebnis: `<name>-geschwaerzt.png` bzw. `<name>-geschwaerzt.pdf` neben dem Original,
oder im gewählten Ablageordner.

## Stand

Gebaut am 24.11.2026 als Fassung für Windows und Linux. Die macOS-Fassung
(`/Applications/Text schwärzen.app`) nutzt Apples Vision-Erkennung und bleibt die
genauere auf dem Mac. Diese Fassung ist der gemeinsame Nenner für alle Systeme.
