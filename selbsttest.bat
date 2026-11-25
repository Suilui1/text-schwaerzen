@echo off
chcp 65001 >nul
cd /d "%~dp0"
del bericht-gezielt.txt bericht-alles.txt bericht-worte.txt bericht.txt 2>nul

echo Text schwaerzen - Selbsttest
echo ==========================
echo.
echo Drei Modi werden an einem Testbild geprueft.
echo.

if not exist "Text-schwaerzen.exe" (
  echo FEHLER: Text-schwaerzen.exe fehlt in diesem Ordner.
  echo Bitte die ZIP vollstaendig entpacken.
  pause
  exit /b 1
)

"Text-schwaerzen.exe" --text pruefung\text.txt --modus gezielt --aus pruefung\aus-text.txt --bericht bericht-text.txt
"Text-schwaerzen.exe" --bild pruefung\test-international.png --modus gezielt --aus pruefung\aus-international.png --bericht bericht-international.txt
"Text-schwaerzen.exe" --bild pruefung\test.png --modus gezielt --aus pruefung\aus-gezielt.png --bericht bericht-gezielt.txt
"Text-schwaerzen.exe" --bild pruefung\test.png --modus alles --aus pruefung\aus-alles.png --bericht bericht-alles.txt
"Text-schwaerzen.exe" --bild pruefung\test.png --modus worte --worte Krause --aus pruefung\aus-krause.png --bericht bericht-worte.txt

rem Jeder Lauf schreibt in eine eigene Datei und wird hier zusammengelegt:
rem die erste Fassung lief dreimal in dieselbe Datei, und im Bericht stand
rem nur noch der letzte Modus (24.11.2026 auf Windows passiert).
echo === gezielt === > bericht.txt
type bericht-gezielt.txt >> bericht.txt
echo. >> bericht.txt
echo === alles === >> bericht.txt
type bericht-alles.txt >> bericht.txt
echo. >> bericht.txt
echo === worte === >> bericht.txt
type bericht-worte.txt >> bericht.txt
echo. >> bericht.txt
echo === internationale Adressen === >> bericht.txt
type bericht-international.txt >> bericht.txt
echo. >> bericht.txt
echo === Textdatei === >> bericht.txt
type bericht-text.txt >> bericht.txt

echo.
echo ---- Bericht ----
type bericht.txt
echo.
echo Die Bilder liegen in pruefung: aus-gezielt.png, aus-alles.png, aus-krause.png
echo Bitte bericht.txt und die drei Bilder in den Chat ziehen.
explorer pruefung
pause
