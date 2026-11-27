"""Die Oberfläche (Tkinter) — Auswahl, Wortliste, Rahmen, Fortschritt.

Tkinter statt Qt: es steckt in Python schon drin, ist auf Windows, Linux und macOS gleich
und macht das spätere Paket klein.
"""
from __future__ import annotations

import json
import queue
import threading
from pathlib import Path

import tkinter as tk
from tkinter import filedialog, messagebox, ttk

from PIL import Image, ImageTk

from . import namen
from . import regeln
from .erkennung import erkenne_worte
from .masken import schwaeze_datei
from .pdf import schwaeze_pdf
from .__main__ import lade_einstellungen, speichere_einstellungen, ziel_pfad, bericht

TEXTE = {
    "de": {
    "k_oeffnen": "Öffnen",
    "k_loeschen": "Löschen",
    "k_zeigen": "Im Dateimanager zeigen",
    "k_ergebnisse": "Zuletzt bearbeitet",
    "keine_ergebnisse": "Noch keine bearbeitete Datei.",
    "loeschen_frage": "Endgültig entfernen?",
    "k_datei": "Datei öffnen…",
    "k_letzte": "Zuletzt geöffnet",
    "keine_letzte": "Noch keine Datei geöffnet",
    "k_text": "Textdatei schwärzen",
    "k_info": "Info",
    "k_entfernen": "Aus Liste entfernen",
    "k_update": "Nach Updates sehen",
    "update_titel": "Update-Prüfung",
    "update_keine_verbindung": "Die Update-Prüfung braucht eine Internetverbindung. Das Programm selbst arbeitet ohne Netz.",
    "update_unbekannt": "Die Abfrage bei GitHub ist gescheitert.",
    "update_grund": "Grund",
    "update_browser_frage": "Trotzdem die Veröffentlichungsseite im Browser öffnen?",
    "update_aktuell": "Das ist die neueste Fassung (%s).",
    "update_neuer": "Neu veröffentlicht: %s\n\nDiese Fassung ist %s.\n\nZur Veröffentlichung?",
    "update_knoepfe": "In diesem Programm gibt es eine Schaltfläche dafür.",
    "info_text": "Text schwärzen — was die App tut\n\nSchwärzt Text in Bildern, PDFs und Textdateien. Alles geschieht auf diesem Rechner; nichts wird verschickt.\n\nERKENNUNG\nTesseract mit deutscher Sprachdatei. Weitere Sprachen über eine eigene Sprachdatei (tessdata).\n\nSCHWÄRZEN\n· Nur Adressen und Namen — nach Regeln (E-Mail, Telefon, IBAN, Adressen, Buchungsnummern)\n· Alles — jede erkannte Zeile\n· Nur markierte Wörter — Wörter ankreuzen\n· Rahmen ziehen — Bereich selbst bestimmen\n· Markieren von Hand — Rechtecke, Stempel, Stile\n\nBEI PDFS\nJede Seite wird als Bild gerendert und neu geschrieben. Der Text ist danach nicht mehr markierbar oder kopierbar.\n\nGRENZE\nEin Blick auf das Ergebnis bleibt nötig. Namen ohne Adresse oder Nummer erkennt die Automatik nicht sicher.\n\nQuelltext: github.com/Suilui1/text-schwaerzen",
    "titel": "Text schwärzen",
    "wie_vorgehen": "Wie vorgehen?",
    "was_schwaerzen": "Was schwärzen?",
    "k_auto": "Automatisch schwärzen",
    "k_selbst": "Selbst markieren",
    "k_gezielt": "Nur Adressen und Namen",
    "k_alles": "Alles",
    "k_worte": "Nur markierte Wörter",
    "k_rahmen": "Rahmen ziehen",
    "k_abbrechen": "Abbrechen",
    "k_schwaerzen": "Schwärzen",
    "k_alle": "Alle",
    "k_keine": "Keine",
    "k_ablage": "Ergebnis-Ordner wählen…",
    "ablage_original": "Ablage: neben dem Original",
    "wort_titel": "Wörter auswählen",
    "wort_text": "Angekreuzte Wörter werden geschwärzt — im Bild an ihrer Stelle, im PDF auf allen Seiten.",
    "start_text": "Schwärzt Text in Bildern, PDFs und Textdateien. Alles geschieht auf diesem Rechner; nichts wird verschickt.",
    "rahmen_titel": "Rahmen um das ziehen, was LESBAR bleiben soll — Enter schwärzt den Rest",
    "seite": "Seite",
    "von": "von",
    "fertig": "Fertig",
    "bild_waehlen": "Welches Bild oder PDF schwärzen?",
    "k_app": "In der App öffnen",
    "datei_fehlt": "Datei fehlt.",
    "k_beenden": "Beenden"
},
    "en": {
    "k_oeffnen": "Open",
    "k_loeschen": "Delete",
    "k_zeigen": "Show in file manager",
    "k_ergebnisse": "Recently edited",
    "keine_ergebnisse": "No edited file yet.",
    "loeschen_frage": "Remove permanently?",
    "k_datei": "Open file…",
    "k_letzte": "Recently opened",
    "keine_letzte": "No file opened yet",
    "k_text": "Redact a text file",
    "k_info": "Info",
    "k_entfernen": "Remove from list",
    "k_update": "Check for updates",
    "update_titel": "Update check",
    "update_keine_verbindung": "The update check needs an internet connection. The program itself works offline.",
    "update_unbekannt": "The query to GitHub failed.",
    "update_grund": "Reason",
    "update_browser_frage": "Open the release page in the browser anyway?",
    "update_aktuell": "This is the latest version (%s).",
    "update_neuer": "Newly released: %s\n\nThis version is %s.\n\nOpen the release page?",
    "update_knoepfe": "The program has a button for this.",
    "info_text": "Redact text — what this app does\n\nRedacts text in images, PDFs and text files. Everything happens on this machine; nothing is sent anywhere.\n\nRECOGNITION\nTesseract with the German language file. Other languages need their own language file (tessdata).\n\nREDACTION\n· Addresses and names only — by rules (email, phone, IBAN, addresses, booking numbers)\n· Everything — every recognised line\n· Marked words only — tick words\n· Draw a box — decide the area yourself\n· Mark by hand — rectangles, stamps, styles\n\nPDFs\nEvery page is rendered as an image and rewritten. The text can no longer be selected or copied.\n\nLIMIT\nAlways look at the result. Names without an address or number are not reliably detected.\n\nSource: github.com/Suilui1/text-schwaerzen",
    "titel": "Redact text",
    "wie_vorgehen": "How to proceed?",
    "was_schwaerzen": "What to redact?",
    "k_auto": "Redact automatically",
    "k_selbst": "Mark it myself",
    "k_gezielt": "Addresses and names only",
    "k_alles": "Everything",
    "k_worte": "Selected words only",
    "k_rahmen": "Draw a box",
    "k_abbrechen": "Cancel",
    "k_schwaerzen": "Redact",
    "k_alle": "All",
    "k_keine": "None",
    "k_ablage": "Choose results folder…",
    "ablage_original": "Saved next to the original",
    "wort_titel": "Choose words",
    "wort_text": "Checked words are redacted — in place in images, on every page in PDFs.",
    "start_text": "Redacts text in images, PDFs and text files. Everything happens on this machine; nothing is sent anywhere.",
    "rahmen_titel": "Draw a box around what should stay READABLE — Enter redacts the rest",
    "seite": "Page",
    "von": "of",
    "fertig": "Done",
    "bild_waehlen": "Which image or PDF should be redacted?",
    "k_app": "Open in the app",
    "datei_fehlt": "File is missing.",
    "k_beenden": "Quit"
},
}

TEXTE["fr"] = {
    "k_oeffnen": "Ouvrir",
    "k_loeschen": "Supprimer",
    "k_zeigen": "Afficher dans le gestionnaire de fichiers",
    "k_ergebnisse": "Modifiés récemment",
    "keine_ergebnisse": "Aucun fichier modifié pour l'instant.",
    "loeschen_frage": "Supprimer définitivement ?",
    "k_datei": "Ouvrir un fichier…",
    "k_letzte": "Ouverts récemment",
    "keine_letzte": "Aucun fichier ouvert pour l'instant",
    "k_text": "Masquer un fichier texte",
    "k_info": "Infos",
    "k_entfernen": "Retirer de la liste",
    "k_update": "Rechercher des mises à jour",
    "update_titel": "Recherche de mise à jour",
    "update_keine_verbindung": "La recherche de mise à jour a besoin d'une connexion Internet. Le programme lui-même fonctionne hors ligne.",
    "update_unbekannt": "La requête à GitHub a échoué.",
    "update_grund": "Raison",
    "update_browser_frage": "Ouvrir quand même la page de publication dans le navigateur ?",
    "update_aktuell": "C'est la version la plus récente (%s).",
    "update_neuer": "Nouvelle publication : %s\n\nCette version est la %s.\n\nOuvrir la page de publication ?",
    "update_knoepfe": "Ce programme propose un bouton pour cela.",
    "info_text": "Text schwärzen — ce que fait l'application\n\nMasque le texte dans les images, les PDF et les fichiers texte. Tout se passe sur cet appareil, sans réseau et sans compte. Les PDF sont réécrits page par page en images : le texte n'est plus sélectionnable.",
    "titel": "Text schwärzen",
    "wie_vorgehen": "Comment procéder ?",
    "was_schwaerzen": "Que masquer ?",
    "k_auto": "Masquer automatiquement",
    "k_selbst": "Marquer soi-même",
    "k_gezielt": "Adresses et noms uniquement",
    "k_alles": "Tout",
    "k_worte": "Seulement les mots marqués",
    "k_rahmen": "Dessiner un cadre",
    "k_abbrechen": "Annuler",
    "k_schwaerzen": "Masquer",
    "k_alle": "Tous",
    "k_keine": "Aucun",
    "k_ablage": "Choisir le dossier des résultats…",
    "ablage_original": "Dossier : à côté de l'original",
    "wort_titel": "Choisir les mots",
    "wort_text": "Les mots cochés sont masqués — dans l'image à leur place, dans le PDF sur toutes les pages.",
    "start_text": "Masque le texte dans les images, les PDF et les fichiers texte. Tout se passe sur cet ordinateur ; rien n'est envoyé.",
    "rahmen_titel": "Tracer un cadre autour de ce qui doit RESTER lisible — Entrée masque le reste",
    "seite": "Page",
    "von": "sur",
    "fertig": "Terminé",
    "bild_waehlen": "Quelle image ou quel PDF masquer ?",
    "k_app": "Ouvrir dans l'application",
    "datei_fehlt": "Fichier introuvable.",
    "k_beenden": "Quitter"
}

TEXTE["es"] = {
    "k_oeffnen": "Abrir",
    "k_loeschen": "Borrar",
    "k_zeigen": "Mostrar en el gestor de archivos",
    "k_ergebnisse": "Editados recientemente",
    "keine_ergebnisse": "Todavía no hay ningún archivo editado.",
    "loeschen_frage": "¿Eliminar definitivamente?",
    "k_datei": "Abrir archivo…",
    "k_letzte": "Abiertos recientemente",
    "keine_letzte": "Todavía no se ha abierto ningún archivo",
    "k_text": "Ocultar un archivo de texto",
    "k_info": "Información",
    "k_entfernen": "Quitar de la lista",
    "k_update": "Buscar actualizaciones",
    "update_titel": "Comprobación de actualizaciones",
    "update_keine_verbindung": "La comprobación necesita conexión a Internet. El programa funciona sin red.",
    "update_unbekannt": "La consulta a GitHub ha fallado.",
    "update_grund": "Motivo",
    "update_browser_frage": "¿Abrir igualmente la página de la publicación en el navegador?",
    "update_aktuell": "Esta es la versión más reciente (%s).",
    "update_neuer": "Nueva publicación: %s\n\nEsta versión es la %s.\n\n¿Abrir la página de la publicación?",
    "update_knoepfe": "En este programa hay un botón para eso.",
    "info_text": "Text schwärzen — qué hace la aplicación\n\nOculta el texto en imágenes, PDF y archivos de texto. Todo ocurre en este equipo, sin red y sin cuenta. Los PDF se reescriben página a página como imágenes: el texto ya no se puede seleccionar.",
    "titel": "Text schwärzen",
    "wie_vorgehen": "¿Cómo proceder?",
    "was_schwaerzen": "¿Qué ocultar?",
    "k_auto": "Ocultar automáticamente",
    "k_selbst": "Marcar uno mismo",
    "k_gezielt": "Solo direcciones y nombres",
    "k_alles": "Todo",
    "k_worte": "Solo las palabras marcadas",
    "k_rahmen": "Dibujar un recuadro",
    "k_abbrechen": "Cancelar",
    "k_schwaerzen": "Ocultar",
    "k_alle": "Todas",
    "k_keine": "Ninguna",
    "k_ablage": "Elegir carpeta de resultados…",
    "ablage_original": "Carpeta: junto al original",
    "wort_titel": "Elegir palabras",
    "wort_text": "Las palabras marcadas se ocultan — en la imagen en su sitio, en el PDF en todas las páginas.",
    "start_text": "Oculta el texto en imágenes, PDF y archivos de texto. Todo ocurre en este equipo; no se envía nada.",
    "rahmen_titel": "Dibujar un recuadro alrededor de lo que debe QUEDAR legible — Intro oculta el resto",
    "seite": "Página",
    "von": "de",
    "fertig": "Listo",
    "bild_waehlen": "¿Qué imagen o PDF ocultar?",
    "k_app": "Abrir en la aplicación",
    "datei_fehlt": "Falta el archivo.",
    "k_beenden": "Salir"
}

TEXTE["zh"] = {
    "k_oeffnen": "打开",
    "k_loeschen": "删除",
    "k_zeigen": "在文件管理器中显示",
    "k_ergebnisse": "最近编辑",
    "keine_ergebnisse": "还没有编辑过的文件。",
    "loeschen_frage": "确定永久删除？",
    "k_datei": "打开文件…",
    "k_letzte": "最近打开",
    "keine_letzte": "还没有打开过文件",
    "k_text": "遮蔽文本文件",
    "k_info": "信息",
    "k_entfernen": "从列表中移除",
    "k_update": "检查更新",
    "update_titel": "检查更新",
    "update_keine_verbindung": "检查更新需要网络连接。程序本身无需联网。",
    "update_unbekannt": "向 GitHub 查询失败。",
    "update_grund": "原因",
    "update_browser_frage": "仍要在浏览器中打开发布页面吗？",
    "update_aktuell": "这是最新版本（%s）。",
    "update_neuer": "有新发布：%s\n\n当前版本是 %s。\n\n要打开发布页面吗？",
    "update_knoepfe": "本程序中有对应的按钮。",
    "info_text": "Text schwärzen — 这个应用做什么\n\n遮蔽图片、PDF 和文本文件中的文字。全部在本机完成，不联网、不需要账户。PDF 会逐页重新写成图片，文字不再可以选中。",
    "titel": "Text schwärzen",
    "wie_vorgehen": "如何进行？",
    "was_schwaerzen": "遮蔽哪些内容？",
    "k_auto": "自动遮蔽",
    "k_selbst": "自己标记",
    "k_gezielt": "仅地址和姓名",
    "k_alles": "全部",
    "k_worte": "仅标记的词",
    "k_rahmen": "画框",
    "k_abbrechen": "取消",
    "k_schwaerzen": "遮蔽",
    "k_alle": "全部",
    "k_keine": "全不选",
    "k_ablage": "选择结果文件夹…",
    "ablage_original": "文件夹：与原文件相同",
    "wort_titel": "选择词",
    "wort_text": "勾选的词会被遮蔽——图片中在原位置，PDF 中在所有页面。",
    "start_text": "遮蔽图片、PDF 和文本文件中的文字。全部在这台电脑上完成，不会发送任何内容。",
    "rahmen_titel": "在需要保持可读的区域外画框 — 回车遮蔽其余部分",
    "seite": "第",
    "von": "页，共",
    "fertig": "完成",
    "bild_waehlen": "要遮蔽哪张图片或哪个 PDF？",
    "k_app": "在应用中打开",
    "datei_fehlt": "文件不存在。",
    "k_beenden": "退出"
}

def update_abfrage(adresse: str = "", zeitgrenze: float = 10.0) -> tuple[str, str]:
    """Fragt GitHub nach der neuesten Veröffentlichung.

    Rückgabe: (Kennzeichnung, Grund). Bei Erfolg ist der Grund ""; scheitert es, ist die
    Kennzeichnung "" und der Grund nennt die Ursache im Klartext.

    Warum der Grund mitgeliefert wird (26.11.2026): Die Windows-Fassung meldete „neuere
    Veröffentlichung ließ sich nicht lesen — die Update-Prüfung braucht eine
    Internetverbindung". Das war eine falsche Auskunft: der Satz galt für JEDE Ursache.
    Julius stand vor dem Rechner und konnte nicht wissen, was los war.

    Warum die LISTE und nicht /releases/latest: letzteres antwortet mit 404, solange alle
    Fassungen Vorab-Fassungen sind (26.11.2026 gemessen).
    """
    import json
    import ssl
    import urllib.request

    adresse = adresse or ("https://api.github.com/repos/Suilui1/text-schwaerzen/releases")
    anfrage = urllib.request.Request(adresse, headers={
        "User-Agent": "text-schwaerzen", "Accept": "application/vnd.github+json"})
    # Zertifikatsliste mitgeben: Im gepackten Windows-Programm (PyInstaller) fehlt sie,
    # dann scheitert JEDE HTTPS-Abfrage mit CERTIFICATE_VERIFY_FAILED — auf Julius'
    # Windows-Rechner am 26.11.2026 genau so passiert. certifi bringt die Liste mit.
    kontext = None
    try:
        import certifi
        kontext = ssl.create_default_context(cafile=certifi.where())
    except Exception:
        kontext = None
    oeffner = (urllib.request.build_opener(urllib.request.HTTPSHandler(context=kontext))
               if kontext is not None else urllib.request.build_opener())
    try:
        with oeffner.open(anfrage, timeout=zeitgrenze) as antwort:
            roh = antwort.read()
    except Exception as fehler:  # noqa: BLE001 — der Grund gehört in die Meldung
        return "", "%s: %s" % (type(fehler).__name__, fehler)
    try:
        liste = json.loads(roh.decode("utf-8", "replace"))
    except Exception as fehler:  # noqa: BLE001
        return "", "Antwort nicht lesbar (%s): %s" % (type(fehler).__name__, fehler)
    if isinstance(liste, dict) and liste.get("message"):
        # Zum Beispiel: „API rate limit exceeded"
        return "", "GitHub sagt: %s" % liste.get("message")
    if not isinstance(liste, list) or not liste:
        return "", "Die Liste der Veröffentlichungen ist leer."
    return str(liste[0].get("tag_name", "") or ""), ""


def neueste_fassung(adresse: str = "", zeitgrenze: float = 10.0) -> str:
    """Nur die Kennzeichnung (leer bei Fehler) — für Prüfskripte."""
    return update_abfrage(adresse, zeitgrenze)[0]


def ist_ergebnis(datei: Path) -> bool:
    """Hat die App diese Datei erzeugt? Nur solche darf die Liste löschen.

    Dieselbe Regel wie in Android (`istErgebnis = stufe.endsWith("-aus")`) und in der
    macOS-App: <name>-geschwaerzt.* oder <name>-markiert.* — Originale nie (25.11.2026).
    """
    name = datei.stem.lower()
    return namen.ist_bearbeitet(name)


def sprache_waehlen() -> str:
    """Deutsch erkennen — auf allen drei Systemen.

    Auf macOS lieferte `locale.getlocale()` nichts, deshalb erschien die Oberfläche
    englisch (24.11.2026 gesehen). Jetzt werden Umgebung, Systemsprache und der
    Windows-Name („German_Germany") geprüft.
    """
    import locale
    import os
    import subprocess
    import sys

    kandidaten = [os.environ.get("LANGUAGE", ""), os.environ.get("LC_ALL", ""),
                  os.environ.get("LANG", "")]
    try:
        kandidaten.append(locale.getlocale()[0] or "")
    except Exception:
        pass
    try:
        kandidaten.append(locale.getdefaultlocale()[0] or "")
    except Exception:
        pass

    # macOS: die Systemsprache steht in den Einstellungen, nicht in der Umgebung.
    if sys.platform == "darwin":
        try:
            ausgabe = subprocess.run(["defaults", "read", "-g", "AppleLanguages"],
                                     capture_output=True, text=True, timeout=5).stdout
            # Die Ausgabe ist eine Liste: ( "de-DE" ) — deshalb jedes Wort einzeln prüfen.
            for zeile in ausgabe.replace("(", " ").replace(")", " ").replace('"', " ").split(","):
                if zeile.strip():
                    kandidaten.append(zeile.strip())
        except Exception:
            pass

    # Fünf Sprachen wie in der Mac-Fassung (27.11.2026): de, en, fr, es, zh.
    zuordnung = (
        (("de", "german", "deutsch", "ger"), "de"),
        (("fr", "french", "français", "francais", "fre"), "fr"),
        (("es", "spanish", "español", "espanol", "spa"), "es"),
        (("zh", "chinese", "中文", "chi"), "zh"),
        (("en", "english", "eng"), "en"),
    )
    for wert in kandidaten:
        text = (wert or "").lower().strip()
        for anf, sprache in zuordnung:
            if text.startswith(anf):
                return sprache
    return "en"


class App:
    def __init__(self, sprache: str = "de"):
        self.sprache = sprache
        self.t = TEXTE.get(sprache, TEXTE["de"])
        self.wurzel = tk.Tk()
        self.wurzel.title(self.t["titel"])
        # Keine feste Höhe: auf Windows wurden die unteren Knöpfe abgeschnitten, weil
        # Schrift und DPI anders ausfallen (24.11.2026: „Startfenster unten abgeschnitten,
        # Abbrechen halb verdeckt"). Das Fenster richtet sich nach seinem Inhalt.
        self.wurzel.minsize(440, 0)

        rahmen = ttk.Frame(self.wurzel, padding=16)
        rahmen.pack(fill="both", expand=True)
        ttk.Label(rahmen, text=self.t["titel"],
                  font=("Helvetica", 18, "bold")).pack(anchor="w")
        ttk.Label(rahmen, text=self.t.get("start_text", self.t["wort_text"]), wraplength=380,
                  foreground="#555").pack(anchor="w", pady=(4, 12))
        # Ein Knopf für alle Dateien (Julius' Wunsch 24.11.2026): Bild, PDF und Text
        # werden an der Endung erkannt. Danach die Reihenfolge wie auf Android.
        # Seit 25.11.2026 gibt es keinen eigenen Knopf „Markieren von Hand" mehr —
        # die Wahl steckt im ersten Schritt der Rückfrage (wie in der macOS-Fassung).
        ttk.Button(rahmen, text=self.t.get("k_datei", "Datei wählen"),
                   command=self.datei_waehlen).pack(fill="x", pady=4)
        ttk.Button(rahmen, text=self.t["k_ablage"],
                   command=self.ablage_waehlen).pack(fill="x", pady=4)
        self.ablage_label = ttk.Label(rahmen, text=self.ablage_text(), foreground="#555")
        self.ablage_label.pack(anchor="w", pady=(0, 8))
        ttk.Button(rahmen, text=self.t.get("k_letzte", "Letzte Dateien"),
                   command=self.letzte_zeigen).pack(fill="x", pady=4)
        ttk.Button(rahmen, text=self.t.get("k_ergebnisse", "Ergebnisse"),
                   command=self.ergebnisse_zeigen).pack(fill="x", pady=4)
        ttk.Button(rahmen, text=self.t.get("k_info", "Info"),
                   command=self.info_zeigen).pack(fill="x", pady=4)
        # Der Knopf beendet das Programm — also heißt er auch so (Julius, 27.11.2026:
        # „abbrechen ist doch beenden"). „Abbrechen" bleibt in den Dialogen, wo es stimmt.
        ttk.Button(rahmen, text=self.t.get("k_beenden", "Beenden"),
                   command=self.wurzel.destroy).pack(fill="x", pady=(10, 0))

        # Fenstergröße aus dem Inhalt ableiten. `geometry("")` allein genügte nicht:
        # auf Windows blieb „Abbrechen" weiter abgeschnitten (Bild vom 24.11.2026),
        # weil das Fenster seine angeforderte Größe nicht übernahm. Deshalb ausdrücklich
        # setzen — Breite aus dem Inhalt, Höhe aus dem Inhalt plus etwas Luft.
        self.wurzel.update_idletasks()
        breite = max(460, self.wurzel.winfo_reqwidth())
        hoehe = self.wurzel.winfo_reqheight() + 12
        self.wurzel.minsize(breite, hoehe)
        self.wurzel.geometry("%dx%d" % (breite, hoehe))
        # Zweite Messung kurz nach dem Anzeigen: unter Windows stehen Schriftmaße erst
        # nach dem ersten Zeichnen fest. Ohne das blieb „Abbrechen" verdeckt.
        self.wurzel.after(250, self.groesse_nachziehen)

    def info_zeigen(self) -> None:
        """Zeigt Fassung, Fähigkeiten und Grenzen — in der gewählten Sprache."""
        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t.get("k_info", "Info"))
        rahmen = ttk.Frame(fenster, padding=16)
        rahmen.pack(fill="both", expand=True)
        try:
            from . import __version__ as fassung
        except Exception:
            fassung = "?"
        ttk.Label(rahmen, text=self.t["titel"] + " · " + str(fassung),
                  font=("Helvetica", 14, "bold")).pack(anchor="w")
        text = tk.Text(rahmen, wrap="word", width=64, height=18, relief="flat")
        text.insert("1.0", self.t.get("info_text", ""))
        text.configure(state="disabled")
        text.pack(fill="both", expand=True, pady=(8, 8))
        knoepfe = ttk.Frame(rahmen)
        knoepfe.pack(fill="x", pady=(4, 0))
        # Julius am 26.11.2026: „die win 10 version hat keine update funktion".
        # Jetzt dieselbe Frage wie in der Mac-App — nur über die LISTE der
        # Veröffentlichungen, weil /releases/latest bei Vorab-Fassungen 404 liefert.
        ttk.Button(knoepfe, text=self.t.get("k_update", "Nach Updates sehen"),
                   command=self.update_pruefen).pack(side="left")
        ttk.Button(knoepfe, text=self.t.get("k_fertig", "Fertig") if "k_fertig" in self.t
                   else "OK", command=fenster.destroy).pack(side="right")

    def update_pruefen(self) -> None:
        """Fragt bei GitHub nach der neuesten Fassung und bietet den Weg dorthin an."""
        import webbrowser
        from . import __kennung__

        neueste, grund = update_abfrage()
        if not neueste:
            # Grund nennen UND den Ausweg anbieten: die Seite geht auch ohne Abfrage.
            frage = messagebox.askyesno(
                self.t.get("update_titel", "Update-Prüfung"),
                self.t.get("update_unbekannt",
                           "Die Abfrage bei GitHub ist gescheitert.")
                + "\n\n" + self.t.get("update_grund", "Grund") + ": " + grund
                + "\n\n" + self.t.get("update_browser_frage",
                                        "Trotzdem die Veröffentlichungsseite im Browser öffnen?"))
            if frage:
                webbrowser.open("https://github.com/Suilui1/text-schwaerzen/releases")
            return
        if neueste == __kennung__:
            messagebox.showinfo(self.t.get("update_titel", "Update-Prüfung"),
                                self.t.get("update_aktuell", "Das ist die neueste Fassung (%s).")
                                % neueste)
            return
        frage = self.t.get("update_neuer",
                           "Es gibt eine neuere Fassung: %s\n\nDies ist %s.\n\nZur Veröffentlichung?")
        if messagebox.askyesno(self.t.get("update_titel", "Update-Prüfung"),
                               frage % (neueste, __kennung__)):
            webbrowser.open("https://github.com/Suilui1/text-schwaerzen/releases")

    def groesse_nachziehen(self) -> None:
        try:
            self.wurzel.update_idletasks()
            breite = max(460, self.wurzel.winfo_reqwidth())
            hoehe = self.wurzel.winfo_reqheight() + 12
            self.wurzel.minsize(breite, hoehe)
            self.wurzel.geometry("%dx%d" % (breite, hoehe))
        except Exception:
            pass

    # ---------------------------------------------------------------- Ablage

    def ablage_text(self) -> str:
        ablage = lade_einstellungen().get("ablage")
        return ("Ablage: " + str(ablage)) if ablage else self.t["ablage_original"]

    def ablage_waehlen(self) -> None:
        ordner = filedialog.askdirectory(title=self.t["k_ablage"])
        if ordner:
            daten = lade_einstellungen()
            daten["ablage"] = ordner
            speichere_einstellungen(daten)
        self.ablage_label.config(text=self.ablage_text())

    # ---------------------------------------------------------------- Ablauf

    def datei_waehlen(self) -> None:
        datei = filedialog.askopenfilename(
            title=self.t.get("k_datei", "Datei wählen"),
            filetypes=[("Bilder, PDF und Text",
                        "*.png *.jpg *.jpeg *.tif *.tiff *.webp *.pdf *.txt *.csv *.log *.md"),
                       ("Alle Dateien", "*.*")],
        )
        if not datei:
            return
        self.datei_oeffnen(Path(datei))

    def _letzte_datei(self) -> Path:
        ordner = Path.home() / ".config" / "text-schwaerzen"
        ordner.mkdir(parents=True, exist_ok=True)
        return ordner / "letzte.json"

    def merke_datei(self, datei: Path) -> None:
        """Hält die letzten acht Dateien fest (wie der Android-Verlauf)."""
        import json
        ziel = self._letzte_datei()
        try:
            liste = json.loads(ziel.read_text(encoding="utf-8"))
        except Exception:
            liste = []
        pfad = str(datei)
        liste = [z for z in liste if z != pfad]
        liste.append(pfad)
        try:
            ziel.write_text(json.dumps(liste[-8:], ensure_ascii=False, indent=1), encoding="utf-8")
        except Exception:
            pass

    def letzte_zeigen(self) -> None:
        """Zeigt die zuletzt geöffneten Dateien; ein Klick öffnet sie."""
        import json
        try:
            liste = json.loads(self._letzte_datei().read_text(encoding="utf-8"))
        except Exception:
            liste = []
        liste = [z for z in liste if Path(z).exists()]
        if not liste:
            messagebox.showinfo(self.t.get("k_letzte", "Letzte Dateien"),
                                self.t.get("keine_letzte", "Noch keine Datei geöffnet"))
            return
        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t.get("k_letzte", "Letzte Dateien"))
        fenster.transient(self.wurzel)
        rahmen = ttk.Frame(fenster, padding=12)
        rahmen.pack(fill="both", expand=True)
        for eintrag in reversed(liste):
            pfad = Path(eintrag)

            def oeffne(p: Path = pfad) -> None:
                fenster.destroy()
                self.datei_oeffnen(p)

            zeile = ttk.Frame(rahmen)
            zeile.pack(fill="x", pady=2)
            ttk.Button(zeile, text=pfad.name, width=40,
                       command=oeffne).pack(side="left")
            ttk.Button(zeile, text=self.t.get("k_zeigen", "Im Dateimanager zeigen"),
                       command=lambda d=pfad: self.zeige_im_ordner(d)).pack(side="left", padx=2)
            # Nur den Eintrag wegnehmen — die Datei bleibt (Julius, 25.11.2026).
            ttk.Button(zeile, text=self.t.get("k_entfernen", "Aus Liste entfernen"),
                       command=lambda d=pfad, z=zeile: self.aus_liste_entfernen(d, z)
                       ).pack(side="left", padx=2)
            # Löschen NUR für Ergebnisse; bei Originalen ist der Knopf grau.
            loeschen = ttk.Button(zeile, text=self.t.get("k_loeschen", "Löschen"),
                                  command=lambda d=pfad, z=zeile: self.loesche_aus_liste(d, z))
            loeschen.pack(side="left", padx=2)
            if not ist_ergebnis(pfad):
                loeschen.state(["disabled"])
        ttk.Button(rahmen, text=self.t.get("k_fertig", "Fertig"),
                   command=fenster.destroy).pack(pady=(10, 0))

    def aus_liste_entfernen(self, datei: Path, zeile) -> None:
        """Nimmt den Eintrag aus der Merkliste — die Datei wird nicht angefaßt."""
        import json
        ziel = self._letzte_datei()
        try:
            liste = json.loads(ziel.read_text(encoding="utf-8"))
        except Exception:
            liste = []
        neu = [z for z in liste if z != str(datei)]
        try:
            ziel.write_text(json.dumps(neu, ensure_ascii=False, indent=1), encoding="utf-8")
        except Exception:
            pass
        zeile.destroy()

    def loesche_aus_liste(self, datei: Path, zeile) -> None:
        """Löscht ein ERGEBNIS samt Merkeintrag. Originale kommen hier nie an."""
        if not ist_ergebnis(datei):
            return
        if not messagebox.askyesno(self.t.get("k_loeschen", "Löschen"),
                                   "%s\n\n%s" % (datei,
                                                  self.t.get("loeschen_frage",
                                                             "Endgültig entfernen?"))):
            return
        try:
            datei.unlink()
        except Exception as fehler:  # noqa: BLE001
            messagebox.showerror(self.t.get("k_loeschen", "Löschen"), str(fehler))
            return
        self.aus_liste_entfernen(datei, zeile)

    def ergebnisse_zeigen(self) -> None:
        """Zeigt die zuletzt bearbeiteten Dateien — auch die neben dem Original."""
        # Ablage aus den Einstellungen; ohne Angabe liegt alles neben dem Original,
        # dann suchen wir im Bilder-Ordner des Benutzers.
        eingestellt = lade_einstellungen().get("ablage")
        if eingestellt:
            ordner = Path(str(eingestellt))
        else:
            ordner = Path.home() / "Pictures"
            if not ordner.exists():
                ordner = Path.home() / "Bilder"
            if not ordner.exists():
                ordner = Path.home() / "Desktop"
        # Anlaß (27.11.2026): Julius — „sie werden auch nicht in der liste angezeigt".
        # Die Suche sah nur EINEN Ordner (Ablage oder Bilder); die Ergebnisse liegen aber
        # neben dem Original. Deshalb: Merkliste der geöffneten Dateien mitnehmen und in
        # deren Ordnern nach bearbeiteten Dateien sehen.
        kandidaten: list[Path] = []
        if Path(ordner).exists():
            kandidaten += [p for p in Path(ordner).glob("*") if namen.ist_bearbeitet(p.name)]
        try:
            gemerkt = json.loads(self._letzte_datei().read_text(encoding="utf-8"))
        except Exception:
            gemerkt = []
        for eintrag in gemerkt:
            alt_datei = Path(eintrag)
            if not alt_datei.parent.exists():
                continue
            kandidaten += [p for p in alt_datei.parent.glob(alt_datei.stem + "*")
                           if namen.ist_bearbeitet(p.name) and p.is_file()]
        # Doppelte raus, neueste zuerst
        gesehen: dict[str, Path] = {}
        for p in kandidaten:
            gesehen[str(p)] = p
        treffer = sorted(gesehen.values(), key=lambda p: p.stat().st_mtime, reverse=True)[:20]
        if not treffer:
            messagebox.showinfo(self.t.get("k_ergebnisse", "Ergebnisse"),
                                self.t.get("keine_ergebnisse",
                                           "Noch keine geschwärzte Datei in der Ablage."))
            return
        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t.get("k_ergebnisse", "Ergebnisse"))
        fenster.transient(self.wurzel)
        rahmen = ttk.Frame(fenster, padding=12)
        rahmen.pack(fill="both", expand=True)
        for datei in treffer:
            zeile = ttk.Frame(rahmen)
            zeile.pack(fill="x", pady=2)
            ttk.Label(zeile, text=datei.name, width=42).pack(side="left")
            # „In der App öffnen" statt einer fremden Anwendung (Julius, 27.11.2026:
            # „öffnen öffnet nicht in der app"). Bei Bildern und PDFs ist das der Editor.
            ttk.Button(zeile, text=self.t.get("k_app", "In der App öffnen"),
                       command=lambda d=datei: self.in_app_oeffnen(d)).pack(side="left", padx=2)
            ttk.Button(zeile, text=self.t.get("k_zeigen", "Im Dateimanager zeigen"),
                       command=lambda d=datei: self.zeige_im_ordner(d)).pack(side="left", padx=2)
            ttk.Button(zeile, text=self.t.get("k_loeschen", "Löschen"),
                       command=lambda d=datei, z=zeile: self.loesche_datei(d, z)).pack(side="left")
        ttk.Button(rahmen, text=self.t.get("k_fertig", "Fertig"),
                   command=fenster.destroy).pack(pady=(10, 0))

    def zeige_im_ordner(self, datei: Path) -> None:
        """Oeffnet den Ordner mit der Datei - je System mit eigenem Befehl.

        Julius' Wunsch vom 25.11.2026; auf Android heisst es "Ordner im
        Dateimanager zeigen". macOS und Windows markieren die Datei gleich,
        Linux oeffnet nur den Ordner.
        """
        import subprocess
        import sys
        try:
            if sys.platform == "darwin":
                subprocess.Popen(["open", "-R", str(datei)])
            elif sys.platform.startswith("win"):
                subprocess.Popen(["explorer", "/select,", str(datei)])
            else:
                subprocess.Popen(["xdg-open", str(datei.parent)])
        except Exception as fehler:  # noqa: BLE001
            messagebox.showerror(self.t.get("k_zeigen", "Im Dateimanager zeigen"), str(fehler))

    def in_app_oeffnen(self, datei: Path) -> None:
        """Öffnet eine bearbeitete Datei IM PROGRAMM — nicht in einer fremden Anwendung.

        Anlaß (27.11.2026): Julius — „öffnen öffnet nicht in der app". Bei Bildern und PDFs
        ist das der Markier-Editor; Textdateien gehen an das Systemprogramm.
        """
        if not datei.exists():
            messagebox.showinfo(self.t.get("k_oeffnen", "Öffnen"),
                                str(datei) + "\n\n" + self.t.get("datei_fehlt", "Datei fehlt."))
            return
        if datei.suffix.lower() in {".txt", ".csv", ".log", ".md"}:
            self.oeffne_extern(datei)
            return
        from .editor import oeffne
        try:
            oeffne(datei, self.sprache)
        except Exception as fehler:  # noqa: BLE001
            messagebox.showerror(self.t.get("k_oeffnen", "Öffnen"), str(fehler))

    def oeffne_extern(self, datei: Path) -> None:
        import subprocess
        import sys
        try:
            if sys.platform == "darwin":
                subprocess.Popen(["open", str(datei)])
            elif sys.platform.startswith("win"):
                import os
                os.startfile(str(datei))  # type: ignore[attr-defined]
            else:
                subprocess.Popen(["xdg-open", str(datei)])
        except Exception:
            messagebox.showinfo(self.t.get("k_oeffnen", "Öffnen"), str(datei))

    def loesche_datei(self, datei: Path, zeile) -> None:
        if not messagebox.askyesno(self.t.get("k_loeschen", "Löschen"),
                                   "%s\n\n%s" % (datei.name,
                                                  self.t.get("loeschen_frage",
                                                             "Endgültig entfernen?"))):
            return
        try:
            datei.unlink()
            zeile.destroy()
        except Exception as fehler:  # noqa: BLE001
            messagebox.showerror(self.t.get("k_loeschen", "Löschen"), str(fehler))

    def datei_oeffnen(self, datei: Path) -> None:
        """Entscheidet an der Endung: Textdatei oder Bild/PDF."""
        self.merke_datei(datei)
        if datei.suffix.lower() in {".txt", ".csv", ".log", ".md"}:
            self.textdatei_schwaerzen(datei)
        else:
            self.verarbeite(datei)

    def textdatei_schwaerzen(self, datei: Path) -> None:
        """Textdatei schwärzen und daneben ablegen."""
        modus = self.frage_modus_text(datei.name)
        if modus is None:
            return
        from .textdatei import schwærze_datei
        try:
            ziel, zaehler, getroffen = schwærze_datei(Path(datei), modus)
        except Exception as fehler:  # noqa: BLE001 — Meldung statt Absturz
            messagebox.showerror(self.t.get("k_text", "Textdatei schwärzen"), str(fehler))
            return
        self.merke_datei(ziel)          # Ergebnis gehört in die Liste (27.11.2026)
        teile = ", ".join("%s %d" % (self.t.get("art_" + art, art), zahl)
                          for art, zahl in zaehler.items()) or self.t.get("nichts", "nichts")
        messagebox.showinfo(self.t["titel"],
                            "%s\n%s\n\n%s" % (ziel.name, teile, getroffen))

    def frage_modus_text(self, dateiname: str = "") -> str | None:
        """Nur zwei Stufen — einen Rahmen und Markieren gibt es bei Text nicht."""
        return self.frage_knopf(self.t["was_schwaerzen"],
                                [("gezielt", self.t["k_gezielt"]),
                                 ("alles", self.t["k_alles"])], dateiname)

    def frage_knopf(self, titel: str, optionen: list[tuple[str, str]],
                    dateiname: str = "") -> str | None:
        """Eine Rückfrage mit Knöpfen; gibt den Wert des gedrückten Knopfes zurück.

        Eigene Knöpfe statt `messagebox`: bei vier Knöpfen ist die Antwort nicht mehr
        eindeutig (so steht es auch in der macOS-Fassung). Abbrechen gibt None.
        """
        fenster = tk.Toplevel(self.wurzel)
        fenster.title(titel)
        fenster.transient(self.wurzel)
        fenster.resizable(False, False)
        fenster.grab_set()
        wahl: list[str | None] = [None]
        ttk.Label(fenster, text=titel, font=("Helvetica", 14, "bold")).pack(padx=20, pady=(14, 2))
        if dateiname:
            # Der Name gehört dazu: sonst weiß niemand mehr, welche Datei gemeint ist,
            # wenn der Dateidialog geschlossen ist.
            ttk.Label(fenster, text=dateiname, foreground="#555",
                      wraplength=340).pack(padx=20, pady=(0, 8))

        def setze(wert: str) -> None:
            wahl[0] = wert
            fenster.destroy()

        for wert, text in optionen:
            ttk.Button(fenster, text=text, width=34,
                       command=lambda w=wert: setze(w)).pack(padx=20, pady=3)
        ttk.Button(fenster, text=self.t["k_abbrechen"], width=34,
                   command=fenster.destroy).pack(padx=20, pady=(10, 16))
        self.wurzel.wait_window(fenster)
        return wahl[0]

    def frage_modus(self, ist_pdf: bool, dateiname: str = "") -> str | None:
        """Fragt in zwei Schritten: erst das Vorgehen, dann die Feinheiten.

        Vorher standen vier Knöpfe auf einmal im Fenster („Wie stark schwärzen?").
        Julius hat am 25.11.2026 für die macOS-Fassung entschieden: zwei Schritte,
        alles so klar und einfach wie nötig. Die Tk-Fassung folgt jetzt nach.
        """
        erstes = self.frage_knopf(self.t["wie_vorgehen"],
                                  [("automatisch", self.t["k_auto"]),
                                   ("markieren", self.t["k_selbst"])],
                                  dateiname)
        if erstes is None:
            return None
        if erstes == "markieren":
            return "markieren"
        optionen = [("gezielt", self.t["k_gezielt"]), ("alles", self.t["k_alles"]),
                    ("worte", self.t["k_worte"])]
        if not ist_pdf:
            optionen.append(("bereich", self.t["k_rahmen"]))
        return self.frage_knopf(self.t["was_schwaerzen"], optionen, dateiname)

    def frage_worte(self, bildpfad: Path) -> set[str] | None:
        woerter = erkenne_worte(bildpfad)
        zaehler: dict[str, int] = {}
        for wort in woerter:
            zaehler[wort.text] = zaehler.get(wort.text, 0) + 1
        if not zaehler:
            return set()

        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t["wort_titel"])
        fenster.transient(self.wurzel)
        fenster.grab_set()
        ttk.Label(fenster, text=self.t["wort_text"], wraplength=420,
                  foreground="#555").pack(padx=16, pady=(12, 8), anchor="w")

        leinwand = tk.Canvas(fenster, width=480, height=380)
        leinwand.pack(side="left", fill="both", expand=True, padx=(16, 0))
        rollbalken = ttk.Scrollbar(fenster, orient="vertical", command=leinwand.yview)
        rollbalken.pack(side="right", fill="y", padx=(0, 16))
        leinwand.configure(yscrollcommand=rollbalken.set)
        innen = ttk.Frame(leinwand)
        leinwand.create_window((0, 0), window=innen, anchor="nw")

        kaestchen: list[tuple[tk.BooleanVar, str]] = []
        for text, anzahl in sorted(zaehler.items(), key=lambda p: (-p[1], p[0])):
            var = tk.BooleanVar(value=False)
            beschriftung = "%s  (%d×)" % (text, anzahl) if anzahl > 1 else text
            ttk.Checkbutton(innen, text=beschriftung, variable=var).pack(anchor="w")
            kaestchen.append((var, text))
        innen.update_idletasks()
        leinwand.configure(scrollregion=leinwand.bbox("all"))

        ergebnis: list[set[str] | None] = [None]

        def alle(wert: bool) -> None:
            for var, _ in kaestchen:
                var.set(wert)

        knoepfe = ttk.Frame(fenster)
        knoepfe.pack(side="bottom", fill="x", padx=16, pady=12)
        ttk.Button(knoepfe, text=self.t["k_alle"], command=lambda: alle(True)).pack(side="left")
        ttk.Button(knoepfe, text=self.t["k_keine"], command=lambda: alle(False)).pack(side="left", padx=6)

        def uebernehmen() -> None:
            gewaehlt = {text for var, text in kaestchen if var.get()}
            ergebnis[0] = gewaehlt or None
            fenster.destroy()

        ttk.Button(knoepfe, text=self.t["k_schwaerzen"], command=uebernehmen).pack(side="right")
        ttk.Button(knoepfe, text=self.t["k_abbrechen"],
                   command=fenster.destroy).pack(side="right", padx=6)
        self.wurzel.wait_window(fenster)
        return ergebnis[0]

    def frage_rahmen(self, bildpfad: Path) -> tuple[int, int, int, int] | None:
        with Image.open(bildpfad) as bild:
            anzeige = bild.convert("RGB").copy()
        breite, hoehe = anzeige.size
        faktor = min(1.0, 900 / breite, 640 / hoehe)
        klein = anzeige.resize((int(breite * faktor), int(hoehe * faktor)))

        fenster = tk.Toplevel(self.wurzel)
        fenster.title(self.t["rahmen_titel"])
        fenster.transient(self.wurzel)
        fenster.grab_set()
        foto = ImageTk.PhotoImage(klein)
        leinwand = tk.Canvas(fenster, width=klein.width, height=klein.height)
        leinwand.pack()
        leinwand.create_image(0, 0, anchor="nw", image=foto)
        leinwand.image = foto  # Referenz halten, sonst verschwindet das Bild

        zustand: dict[str, float] = {}
        rechteck: list[int] = [0]

        def starten(event) -> None:
            zustand["x"] = event.x
            zustand["y"] = event.y

        def ziehen(event) -> None:
            if "x" not in zustand:
                return
            if rechteck[0]:
                leinwand.delete(rechteck[0])
            rechteck[0] = leinwand.create_rectangle(zustand["x"], zustand["y"], event.x, event.y,
                                                    outline="green", width=2)

        leinwand.bind("<ButtonPress-1>", starten)
        leinwand.bind("<B1-Motion>", ziehen)

        ergebnis: list[tuple[int, int, int, int] | None] = [None]

        def uebernehmen(_event=None) -> None:
            kasten = leinwand.coords(rechteck[0]) if rechteck[0] else None
            if not kasten:
                ergebnis[0] = None
            else:
                x1, y1, x2, y2 = kasten
                ergebnis[0] = (int(min(x1, x2) / faktor), int(min(y1, y2) / faktor),
                               int(max(x1, x2) / faktor), int(max(y1, y2) / faktor))
            fenster.destroy()

        leinwand.bind("<Return>", uebernehmen)
        ttk.Button(fenster, text=self.t["k_schwaerzen"], command=uebernehmen).pack(pady=6)
        fenster.wait_window()
        return ergebnis[0]

    def oeffne_editor(self, quelle: Path) -> None:
        """„Selbst markieren" — der Markier-Editor, mit allen PDF-Seiten.

        Fehlt PyMuPDF (nur bei PDF nötig) oder scheitert das Öffnen, erscheint eine
        Meldung statt eines Absturzes: bis 25.11.2026 wäre hier ein Traceback
        erschienen, und das Fenster hätte wortlos nichts getan.
        """
        try:
            from .editor import oeffne
            oeffne(Path(quelle), self.sprache)
        except ImportError as fehler:
            messagebox.showerror(self.t["k_selbst"],
                                 "PyMuPDF fehlt — PDFs lassen sich nicht öffnen.\n"
                                 "pip install pymupdf\n\n%s" % fehler)
        except Exception as fehler:  # noqa: BLE001 — Meldung statt Absturz
            messagebox.showerror(self.t["k_selbst"],
                                 "%s\n\n%s" % (quelle.name, fehler))

    def verarbeite(self, quelle: Path) -> None:
        ist_pdf = quelle.suffix.lower() == ".pdf"
        modus = self.frage_modus(ist_pdf, quelle.name)
        if not modus:
            return
        if modus == "markieren":
            self.oeffne_editor(quelle)
            return

        worte: set[str] = set()
        if modus == "worte":
            if ist_pdf:
                try:
                    import pymupdf as fitz
                except ImportError:
                    import fitz
                import tempfile
                with fitz.open(quelle) as dokument:
                    pixmap = dokument[0].get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
                    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
                        tmp.write(pixmap.tobytes("png"))
                        erste_seite = Path(tmp.name)
                try:
                    gewaehlt = self.frage_worte(erste_seite)
                finally:
                    erste_seite.unlink(missing_ok=True)
            else:
                gewaehlt = self.frage_worte(quelle)
            if not gewaehlt:
                self.wurzel.deiconify()
                return
            worte = gewaehlt

        behalten = None
        if modus == "bereich":
            behalten = self.frage_rahmen(quelle)
            if behalten is None:
                return

        ziel = ziel_pfad(quelle)
        if ist_pdf:
            self.zeige_fortschritt(quelle, ziel, modus, worte, behalten)
        else:
            zahlen = schwaeze_datei(quelle, ziel, modus, worte, behalten)
            self.merke_datei(ziel)      # Ergebnis gehört in die Liste (27.11.2026)
            messagebox.showinfo(self.t["fertig"], self.berichts_text(ziel, zahlen))

    def berichts_text(self, ziel: Path, zahlen: dict) -> str:
        if not zahlen:
            return "nichts Sensibles gefunden\n\n" + str(ziel)
        teile = ", ".join("%s %d" % (regeln.bezeichnung(art, self.sprache), n)
                          for art, n in sorted(zahlen.items()))
        return teile + "\n\n" + str(ziel)

    def zeige_fortschritt(self, quelle: Path, ziel: Path, modus: str,
                          worte: set[str], behalten) -> None:
        fenster = tk.Toplevel(self.wurzel)
        # Vorher stand hier „Wie stark schwärzen?" — die Frage ist aber längst
        # beantwortet, das Fenster zeigt den Fortschritt.
        fenster.title(self.t["titel"])
        fenster.transient(self.wurzel)
        fenster.grab_set()
        beschriftung = ttk.Label(fenster, text="…")
        beschriftung.pack(padx=20, pady=(16, 6))
        balken = ttk.Progressbar(fenster, length=360, mode="determinate")
        balken.pack(padx=20, pady=(0, 16))
        meldungen: queue.Queue = queue.Queue()

        def stand(fertig: int, gesamt: int) -> None:
            meldungen.put((fertig, gesamt))

        ergebnis: dict = {}

        def arbeit() -> None:
            ergebnis["wert"] = schwaeze_pdf(quelle, ziel, modus, worte, behalten,
                                            sprache="deu", stand=stand)

        faden = threading.Thread(target=arbeit, daemon=True)
        faden.start()

        def nachsehen() -> None:
            try:
                while True:
                    fertig, gesamt = meldungen.get_nowait()
                    balken["maximum"] = gesamt
                    balken["value"] = fertig
                    beschriftung.config(text="%s %d %s %d" % (self.t["seite"], fertig,
                                                              self.t["von"], gesamt))
            except queue.Empty:
                pass
            if faden.is_alive():
                fenster.after(120, nachsehen)
            else:
                fenster.destroy()
                seiten, zahlen = ergebnis.get("wert", (0, {}))
                if ziel.exists():
                    self.merke_datei(ziel)   # Ergebnis gehört in die Liste (27.11.2026)
                messagebox.showinfo(self.t["fertig"], self.berichts_text(ziel, zahlen))

        fenster.after(120, nachsehen)
        self.wurzel.wait_window(fenster)

    def laufen(self) -> int:
        self.wurzel.mainloop()
        return 0


def laufe() -> int:
    return App(sprache_waehlen()).laufen()
