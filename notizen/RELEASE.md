# Text schwärzen 2.9.0-alpha.12

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
| `Text-schwaerzen-macos-app.zip` | **macOS-App** — braucht nichts weiter (Apple Silicon und Intel, ab macOS 13) |
| `Text-schwaerzen-macos-rechner.zip` | macOS (Rechner-Fassung, braucht Tesseract, nur Apple Silicon) |

Die macOS-App ist neu dabei: entpacken, in den Ordner *Programme* ziehen, beim ersten
Start **Rechtsklick → Öffnen** (die App ist nicht bei Apple notarisiert — das wäre ein
bezahltes Entwicklerkonto). Sie erkennt Text mit Apples Vision und braucht kein brew,
keinen Tesseract, kein Python.

Die **Rechner-Fassung** (`Text-schwaerzen-macos-rechner.zip`) bleibt für Macs unterhalb von
macOS 14 — sie braucht Tesseract (`brew install tesseract`).

## Neu in dieser Fassung## Neu in dieser Fassung

- **Farbe und Dicke wählbar** in der Rechner-Fassung (Windows, Linux, macOS): vorher war
  die Farbe je Werkzeug fest verdrahtet, eine Strichdicke gab es gar nicht. Beide Felder
  sind ausgegraut, wo sie nichts bewirken (Dicke nicht beim Verwischen, Farbe nicht beim
  Verwischen).
- **Große Bilder werden beim Öffnen eingepaßt.** Ein Foto aus der Kamera (3000 × 2000 und
  größer) erschien bei 100 % als Ausschnitt; jetzt paßt es ins Fenster, und die Anzeige
  zeigt den Maßstab („29 %"). Dazu: **Mausrad zoomt**, und ein Knopf „Einpassen" holt das
  ganze Bild zurück.

- **Bearbeitete Dateien heißen jetzt `<name>-bearbeitet`** — in allen Fassungen, im
  englischen Programm `<name>-edited`. Vorher stand dort `-geschwaerzt` (Regeln, Wörter,
  Rahmen) oder `-markiert` (Editor): zwei Namen für dieselbe Sache, und im englischen
  Programm der deutsche Zusatz.
- Dateien mit den alten Namen gelten weiter als Ergebnisse — wer vorher geschwärzt hat,
  findet seine Dateien unverändert (sie werden nicht plötzlich zu Originalen erklärt).

- **macOS: die Formatwahl im Speicherdialog ist jetzt wirklich zu sehen.** Die App bot
  PNG/JPEG/PDF schon an, aber `NSSavePanel` zeigt von sich aus keine Auswahl — sie muß als
  Zusatzleiste ins Fenster. Jetzt steht dort „Format: PNG · JPEG · PDF", und die Endung im
  Dateinamen zieht mit.

- **Formate beim Speichern.** Bearbeitete Bilder lassen sich jetzt als **PNG** (scharf,
  für Screenshots), **JPEG** (klein, für Fotos) oder **PDF** (eine Seite, zum Weitergeben)
  speichern — in allen drei Fassungen. Textdateien bleiben TXT; bei Zeilen-Schwärzung gibt
  es kein sinnvolles zweites Format.

- **Android: die Vorschau im Startfenster zeigt jetzt das letzte Ergebnis.** Nach einer
  PDF-Bearbeitung stand dort noch das Bild der vorigen Bearbeitung — ein Tippen darauf
  öffnete deshalb etwas anderes als das Angezeigte. Die Vorschau kommt jetzt aus der
  ersten Seite des Ergebnis-PDF.

- **Blättern in mehrseitigen PDFs** — in allen Fassungen.
  In der Mac-App lief der Seitenzähler mit, während das Bild auf Seite 1 stehenblieb
  (die Ansicht hielt eine verkleinerte Anzeigekopie und baute sie nur bei geänderter
  Größe neu). Behoben, die Pfeile sind an den Enden jetzt ausgegraut.
- **Marken je Seite** in der Mac-App und in der Android-App: was man auf Seite 2 malt,
  erscheint nicht mehr auf jeder Seite; beim Speichern bekommt jede Seite ihre Marken.
- **Android kann jetzt im PDF blättern:** der Markier-Editor bekommt das PDF selbst,
  zeigt eine Seitenleiste und speichert seitenweise.
- Die Rechner-Fassung (Windows, Linux) konnte das bereits — nachgemessen, nicht geändert.
## Neu in der vorigen Fassung (alpha.2)

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
