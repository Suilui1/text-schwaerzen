# Text schwärzen 2.9.0-alpha.2

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
| `Text-schwaerzen-macos.zip` | macOS (Rechner-Fassung) |

Die macOS-App mit den Markier-Werkzeugen (Swift) liegt **nicht** im ZIP — sie wird
als Bündel in `/Applications` installiert und ist in dieser Veröffentlichung nicht enthalten.

## Neu in dieser Fassung

- **Zwei Schritte statt vier Knöpfe auf einmal:** erst „Wie vorgehen?" (Automatisch
  schwärzen · Selbst markieren), dann „Was schwärzen?". Der Dateiname steht dabei im Fenster.
- **Nur nötige Felder sind bedienbar:** Verwisch-Stil, Schriftgröße und Stempel sind
  ausgegraut, wenn das gewählte Werkzeug sie nicht benutzt; „Rückgängig" ist grau,
  solange nichts gezeichnet ist.
- **Liste der letzten Dateien:** „Aus Liste entfernen" (die Datei bleibt) und „Löschen".
  Gelöscht werden können nur Ergebnisse der App (`…-geschwaerzt`, `…-markiert`) —
  bei Originalen ist der Knopf ausgegraut, wie in der Android-Fassung.
- **Ohne PyMuPDF geht mehr:** Bilder und Textdateien lassen sich jetzt auch dann
  schwärzen, wenn PyMuPDF fehlt; es wird nur für PDFs gebraucht.

## Geprüft

- Android: neun Unit-Tests laufen im Bau vor dem Paketieren
- Rechner-Fassung: Bau grün für Windows, Linux, macOS · Prüfskript
  `pruefe_rechner_fassung.py` (Startfenster, Liste, Felder je Werkzeug, Bild- und
  Textdatei-Weg) · Gegenprobe gegen den vorigen Stand schlägt an
- macOS-App (Swift): Selbstprüfung der Modi — gezielt 3, alles 4, Rahmen 1 Fläche;
  PDF mit zwei Seiten geschrieben und wieder gelesen; Markieren nimmt die gewählte
  Farbe; Vorschau und Ergebnis stimmen für sechs Werkzeuge überein; Verwischen
  hinterläßt keinen lesbaren Text (Texterkennung: 16 Wörter → 0)

## Bekannte Grenzen

- **Windows und Linux sind ungetestet** — gebaut, aber nicht auf einem echten Gerät gelaufen.
- Die macOS-App (Swift) ist neu: Werkzeuge, Farben, Größen, PDF-Seiten funktionieren im
  Selbsttest, aber Maus und Tastatur hat noch niemand länger benutzt als eine Stunde.
- Kyrillisch, Griechisch, Arabisch, Hebräisch erkennt die Texterkennung nicht.
- Das Ergebnis ist ein Bild-PDF: nicht mehr durchsuchbar — das ist Absicht.
- Die Alpha ist nicht im Play Store; für Android muß die Installation aus unbekannten
  Quellen einmal erlaubt werden.

## Rückmeldung

Bitte mit Gerät, Android-/Windows-Fassung und dem, was erwartet wurde. Stürzt die App ab,
zeigt sie beim nächsten Start einen Bericht mit Datum und Fassung.

Quelltext und Fehlermeldungen: <https://github.com/Suilui1/text-schwaerzen>
