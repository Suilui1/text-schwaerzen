# Text schwärzen 2.9.0-alpha.1

**Vorab-Fassung** — die Android-App und die Rechner-Fassung sind weitgehend geprüft,
die macOS-App ist neu und noch nicht von einer zweiten Person getestet.

## Was das ist

Ein Werkzeug, das Text in Bildern, PDFs und Textdateien schwärzt — auf dem Gerät,
ohne Netz, ohne Konto. Bei PDFs wird jede Seite als Bild neu geschrieben; der Text
ist danach nicht mehr markierbar oder kopierbar.

## Pakete

| Datei | System |
|---|---|
| `Text-schwaerzen-android.apk` | Android 8 und neuer |
| `Text-schwaerzen-windows.zip` | Windows 10/11 (mit Tesseract) |
| `Text-schwaerzen-linux.tar.gz` | Linux (x86_64) |
| `Text-schwaerzen-macos.zip` | macOS (Tk-Fassung) |

Die macOS-App mit den Markier-Werkzeugen (Swift) liegt **nicht** im ZIP — sie wird
als Bündel in `/Applications` installiert und ist in dieser Veröffentlichung nicht enthalten.

## Geprüft

- Android: Selbsttest 9 von 9 · Bilder, PDFs, Textdateien · Werkzeuge · PDF-Formen
  auf jeder Seite · Verlauf entdoppelt · Fassung 2.9.0 in „Info"
- Rechner-Fassung: Bau grün für Windows, Linux, macOS · Drehen an beiden Endpunkten
  kopflos geprüft · PDF-Formen je Seite
- macOS-App (Swift): Selbstprüfung der Modi — gezielt 3, alles 4, Rahmen 1 Fläche;
  PDF mit zwei Seiten geschrieben und wieder gelesen

## Bekannte Grenzen

- **Windows und Linux sind ungetestet** — gebaut, aber nicht auf einem echten Gerät gelaufen.
- Die macOS-App (Swift) ist neu: Werkzeuge, Farben, Größen, PDF-Seiten funktionieren im
  Selbsttest, aber Maus und Tastatur hat noch niemand länger benutzt.
- Kyrillisch, Griechisch, Arabisch, Hebräisch erkennt die Texterkennung nicht.
- Das Ergebnis ist ein Bild-PDF: nicht mehr durchsuchbar — das ist Absicht.
- Die Alpha ist nicht im Play Store; für Android muß die Installation aus unbekannten
  Quellen einmal erlaubt werden.

## Rückmeldung

Bitte mit Gerät, Android-/Windows-Fassung und dem, was erwartet wurde. Stürzt die App ab,
zeigt sie beim nächsten Start einen Bericht mit Datum und Fassung.

Quelltext und Fehlermeldungen: <https://github.com/Suilui1/text-schwaerzen>
