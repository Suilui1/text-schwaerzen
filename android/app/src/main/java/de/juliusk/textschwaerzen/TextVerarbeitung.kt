package de.juliusk.textschwaerzen

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * Textdateien (.txt) schwärzen.
 *
 * Julius' Wunsch vom 24.11.2026: „es fehlt text öffnen". Bilder und PDFs gingen
 * schon; eine einfache Textdatei ließ sich nicht laden.
 *
 * Geschwärzt wird zeilenweise: trifft eine Regel, wird der Inhalt der Zeile durch
 * Balken ersetzt (Länge bleibt, damit die Datei lesbar bleibt).
 */
object TextVerarbeitung {

    /** Dateiart, wie sie der eine Knopf erkennen muss. */
    enum class Art { BILD, PDF, TEXT }

    /** Erkennt die Art an Endung und MIME-Typ — reine Logik, ohne Gerät prüfbar. */
    fun artBestimmen(name: String, mime: String): Art {
        val endung = name.substringAfterLast('.', "").lowercase()
        val typ = mime.lowercase()
        return when {
            typ == "application/pdf" || endung == "pdf" -> Art.PDF
            typ.startsWith("text/") || endung in setOf("txt", "csv", "log", "md") -> Art.TEXT
            else -> Art.BILD
        }
    }

    /** Schwärzt Text zeilenweise — ohne Datei, damit es sich prüfen lässt. */
    fun schwaerzeInhalt(inhaltRoh: String, modus: String): Triple<String, Map<String, Int>, Int> {
        // Windows-Dateien haben CRLF — sonst bleibt ein \r am Zeilenende stehen.
        val inhalt = inhaltRoh.replace("\r\n", "\n").replace("\r", "\n")
        val zaehler = mutableMapOf<String, Int>()
        var getroffen = 0
        val zeilen = inhalt.split("\n").map { zeile ->
            if (zeile.isBlank()) {
                zeile
            } else {
                val arten = Regeln.treffer(zeile, modus)
                if (arten.isEmpty()) {
                    zeile
                } else {
                    val laenge = zeile.trim().length.coerceIn(3, 500)
                    for (art in arten) zaehler[art] = (zaehler[art] ?: 0) + 1
                    getroffen++
                    "█".repeat(laenge)
                }
            }
        }
        return Triple(zeilen.joinToString("\n"), zaehler, getroffen)
    }

    data class Ergebnis(
        val text: String,
        val datei: Uri?,
        val zaehler: Map<String, Int>,
        val zeilen: Int,
        val geschwaerzt: Int,
        val fehler: String = ""
    )

    /** Liest die Datei; UTF-8 zuerst, danach Windows-1252 als Rückfall. */
    fun lesen(context: Context, uri: Uri): String? {
        val rohdaten = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (fehler: Throwable) {
            null
        } ?: return null
        return try {
            String(rohdaten, Charsets.UTF_8)
        } catch (fehler: Throwable) {
            String(rohdaten, charset("windows-1252"))
        }
    }

    fun verarbeite(context: Context, uri: Uri, modus: String, name: String): Ergebnis {
        val inhalt = lesen(context, uri)
            ?: return Ergebnis("", null, emptyMap(), 0, 0, "Datei nicht lesbar")
        val zeilen = inhalt.split("\n")
        val zaehler = mutableMapOf<String, Int>()
        var getroffen = 0
        val neu = StringBuilder()
        for (zeile in zeilen) {
            val arten = if (zeile.isBlank()) emptyList() else Regeln.treffer(zeile, modus)
            if (arten.isEmpty()) {
                neu.append(zeile).append("\n")
            } else {
                // Balken gleicher Länge: der Umriß bleibt erkennbar, der Inhalt ist weg.
                neu.append("█".repeat(zeile.trim().length.coerceIn(3, 500))).append("\n")
                for (art in arten) zaehler[art] = (zaehler[art] ?: 0) + 1
                getroffen++
            }
        }
        val ziel = speichere(context, neu.toString(), name)
        return Ergebnis(neu.toString(), ziel, zaehler, zeilen.size, getroffen)
    }

    /** Zeilen der Datei — für die Auswahl „Bereich ziehen" (Zeilen ankreuzen). */
    fun zeilen(context: Context, uri: Uri): List<String> {
        val inhalt = lesen(context, uri) ?: return emptyList()
        return inhalt.split("\n")
    }

    /** Schwärzt genau die angekreuzten Zeilen (Nummern ab 0). */
    fun verarbeiteAuswahl(context: Context, uri: Uri, gewaehlt: Set<Int>, name: String): Ergebnis {
        val alle = zeilen(context, uri)
        if (alle.isEmpty()) {
            return Ergebnis("", null, emptyMap(), 0, 0, "Datei nicht lesbar")
        }
        val neu = StringBuilder()
        var getroffen = 0
        for ((nummer, zeile) in alle.withIndex()) {
            if (nummer in gewaehlt && zeile.isNotBlank()) {
                neu.append("█".repeat(zeile.trim().length.coerceIn(3, 500))).append("\n")
                getroffen++
            } else {
                neu.append(zeile).append("\n")
            }
        }
        val zaehler = mapOf("auswahl" to getroffen)
        return Ergebnis(neu.toString(), speichere(context, neu.toString(), name), zaehler,
                        alle.size, getroffen)
    }

    private fun speichere(context: Context, text: String, quellName: String): Uri? {
        val name = "$quellName-geschwaerzt.txt"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val werte = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, werte)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { strom ->
                        strom.write(text.toByteArray(Charsets.UTF_8))
                    }
                }
                uri
            } else {
                val ordner = context.getExternalFilesDir(null) ?: context.filesDir
                val datei = java.io.File(ordner, name)
                datei.writeText(text, Charsets.UTF_8)
                Uri.fromFile(datei)
            }
        } catch (fehler: Throwable) {
            null
        }
    }
}
