"""Einstiegspunkt für das gepackte Programm (PyInstaller).

Ohne diese Datei kennt PyInstaller den Startpunkt nicht: `python -m textschwaerzen`
funktioniert nur in der Entwicklung.
"""
import sys

from textschwaerzen.__main__ import befehlszeile, starte_oberflaeche

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] in ("--bild", "--pdf", "--text", "--help", "-h"):
        sys.exit(befehlszeile(sys.argv[1:]))
    sys.exit(starte_oberflaeche())
