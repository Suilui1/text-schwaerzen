#!/bin/sh
# Text schwärzen — Installer für Linux.
#
# Warum so (27.11.2026): Julius — „baue eine leicht installierbare linux version".
# Das fertige PyInstaller-Paket stürzte auf seinem Rechner mit Signal 4 ab (die
# mitgelieferte Python-Bibliothek paßte nicht zum Prozessor). Dieser Weg benutzt das
# PYTHON DES SYSTEMS und installiert nur die Pakete, die wirklich fehlen — damit gibt
# es nichts Mitgeliefertes, das nicht passen könnte.
#
# Aufruf:   ./installieren.sh            (fragt vor jedem sudo)
#           ./installieren.sh --ohne-fragen   (alles installieren, was fehlt)
set -eu

HIER=$(cd "$(dirname "$0")" && pwd)
ZIEL="$HOME/.local/share/text-schwaerzen"
BIN="$HOME/.local/bin"
MENUE="$HOME/.local/share/applications"
FRAGEN=1
[ "${1:-}" = "--ohne-fragen" ] && FRAGEN=0

sag() { printf '%s\n' "$*"; }
fehler() { printf 'FEHLER: %s\n' "$*" >&2; exit 1; }

sag "== 1. Python prüfen =="
command -v python3 >/dev/null 2>&1 || fehler "python3 fehlt. Bitte zuerst installieren (z. B. sudo apt install python3)."
PY=$(command -v python3)
VERSION=$("$PY" -c 'import sys; print("%d.%d" % sys.version_info[:2])')
sag "   $PY (Python $VERSION)"
"$PY" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)' \
  || fehler "Python 3.10 oder neuer wird gebraucht (gefunden: $VERSION)."

sag "== 2. Fehlende Pakete ermitteln =="
fehlt=""
"$PY" -c 'import tkinter' 2>/dev/null || fehlt="$fehlt python3-tk"
"$PY" -c 'import PIL' 2>/dev/null || fehlt="$fehlt python3-pil"
"$PY" -c 'import fitz' 2>/dev/null || fehlt="$fehlt python3-pymupdf"
"$PY" -c 'import pytesseract' 2>/dev/null || fehlt="$fehlt python3-pytesseract"
command -v tesseract >/dev/null 2>&1 || fehlt="$fehlt tesseract-ocr"
if command -v tesseract >/dev/null 2>&1; then
  tesseract --list-langs 2>/dev/null | grep -qx deu || fehlt="$fehlt tesseract-ocr-deu"
fi

if [ -z "$fehlt" ]; then
  sag "   alles vorhanden"
else
  sag "   es fehlt:$fehlt"
  if command -v apt-get >/dev/null 2>&1; then
    if [ "$FRAGEN" = "1" ]; then
      printf 'Jetzt mit apt installieren? Das braucht dein Paßwort. [j/N] '
      read -r antwort
      case "$antwort" in j|J|y|Y) ;; *) sag "   übersprungen — bitte selbst nachinstallieren."; exit 1;; esac
    fi
    sudo apt-get update -qq
    sudo apt-get install -y $fehlt || sag "   apt meldete ein Problem — weiter mit pip"
  fi
  # Was apt nicht kennt, über pip in den Benutzerordner (kein root nötig)
  "$PY" -c 'import PIL' 2>/dev/null || "$PY" -m pip install --user --quiet pillow
  "$PY" -c 'import fitz' 2>/dev/null || "$PY" -m pip install --user --quiet pymupdf
  "$PY" -c 'import pytesseract' 2>/dev/null || "$PY" -m pip install --user --quiet pytesseract
fi

sag "== 3. Programm kopieren =="
mkdir -p "$ZIEL"
rm -rf "$ZIEL/textschwaerzen"
cp -R "$HIER/textschwaerzen" "$ZIEL/"
for datei in README.md LICENSE start.py; do
  [ -f "$HIER/$datei" ] && cp "$HIER/$datei" "$ZIEL/" || true
done
sag "   nach $ZIEL"

sag "== 4. Startbefehl und Menüeintrag =="
mkdir -p "$BIN" "$MENUE"
cat > "$BIN/text-schwaerzen" <<START
#!/bin/sh
# Startet Text schwärzen aus dem Benutzerordner.
cd "$ZIEL" || exit 1
exec "$PY" -m textschwaerzen "\$@"
START
chmod 755 "$BIN/text-schwaerzen"
sag "   $BIN/text-schwaerzen"

cat > "$MENUE/text-schwaerzen.desktop" <<MENUEINT
[Desktop Entry]
Type=Application
Name=Text schwärzen
Comment=Text in Bildern, PDFs und Textdateien unkenntlich machen
Exec=$BIN/text-schwaerzen
Icon=$ZIEL/symbol.png
Terminal=false
Categories=Utility;Graphics;
MENUEINT
[ -f "$HIER/symbol.png" ] && cp "$HIER/symbol.png" "$ZIEL/symbol.png" || true
sag "   $MENUE/text-schwaerzen.desktop"

sag ""
sag "Fertig."
sag "  Starten:  text-schwaerzen        (oder im Menü: Text schwärzen)"
sag "  Prüfen:   text-schwaerzen --updatepruefung"
sag "  Entfernen: rm -rf \"$ZIEL\" \"$BIN/text-schwaerzen\" \"$MENUE/text-schwaerzen.desktop\""
