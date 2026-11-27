package de.juliusk.textschwaerzen

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * PDF auf Android: jede Seite als Bild rendern, schwärzen, als neues PDF speichern.
 *
 * Warum gerastert: Kästen über den Text zu malen sieht gut aus, aber der Text steht weiter
 * darunter und läßt sich markieren und kopieren. Das neue PDF enthält nur Bilder.
 * Alles mit Bordmitteln (PdfRenderer, PdfDocument) — keine zusätzliche Bibliothek.
 */
object PdfVerarbeitung {

    data class Ergebnis(val seiten: Int, val datei: Uri?, val zaehler: Map<String, Int>,
                        val fehler: String = "", val woerter: Int = 0)

    /** Erste Seite als Bilddatei — für den Rahmen-Editor. */
    fun ersteSeiteAlsBild(context: Context, uri: Uri): File? {
        val zwischen = File(context.cacheDir, "pdf-seite1.pdf")
        context.contentResolver.openInputStream(uri)?.use { quelle ->
            zwischen.outputStream().use { ziel -> quelle.copyTo(ziel) }
        } ?: return null
        val descriptor = ParcelFileDescriptor.open(zwischen, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { seite ->
                    val breite = 1200
                    val hoehe = (breite.toFloat() * seite.height / seite.width).toInt()
                    val bild = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
                    Canvas(bild).drawColor(Color.WHITE)
                    seite.render(bild, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val ziel = File(context.cacheDir, "pdf-seite1.png")
                    java.io.FileOutputStream(ziel).use { stroem ->
                        bild.compress(Bitmap.CompressFormat.PNG, 100, stroem)
                    }
                    bild.recycle()
                    ziel
                }
            }
        } catch (fehler: Throwable) {
            null
        } finally {
            descriptor.close()
            zwischen.delete()
        }
    }

    fun verarbeite(context: Context, uri: Uri, erkennung: Erkennung, modus: String,
                   name: String = "dokument",
                   bereich: android.graphics.RectF? = null,
                   stand: ((Int, Int) -> Unit)? = null,
                   abgebrochen: (() -> Boolean)? = null): Ergebnis = try {
        verarbeiteInnen(context, uri, erkennung, modus, name, bereich, stand, abgebrochen)
    } catch (fehler: Throwable) {
        // Statt die App zu beenden: Meldung zurückgeben. Am 24.11.2026 schloß sich die App
        // beim PDF-Schwärzen ohne Meldung — vermutlich Speicher oder ein PDF-Sonderfall.
        Ergebnis(0, null, emptyMap(), "${fehler.javaClass.simpleName}: ${fehler.message}")
    }

    private fun verarbeiteInnen(context: Context, uri: Uri, erkennung: Erkennung, modus: String,
                                name: String, bereich: android.graphics.RectF?,
                                stand: ((Int, Int) -> Unit)?,
                                abgebrochen: (() -> Boolean)?): Ergebnis {
        val zwischen = File(context.cacheDir, "quelle.pdf")
        context.contentResolver.openInputStream(uri)?.use { quelle ->
            zwischen.outputStream().use { ziel -> quelle.copyTo(ziel) }
        } ?: return Ergebnis(0, null, emptyMap())

        val descriptor: ParcelFileDescriptor =
            ParcelFileDescriptor.open(zwischen, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val neuesPdf = PdfDocument()
        val gesamt = mutableMapOf<String, Int>()
        var woerterGesamt = 0

        // Seitenzahl merken, BEVOR der Renderer geschlossen wird: ein Zugriff danach
        // wirft „IllegalStateException: already closed" (Julius' Meldung, 24.11.2026).
        val seitenAnzahl = renderer.pageCount
        try {
            for (nummer in 0 until seitenAnzahl) {
                // Bei vielen Seiten (91 waren es am 24.11.2026) muß man abbrechen können.
                if (abgebrochen != null && abgebrochen()) {
                    neuesPdf.close()
                    return Ergebnis(nummer, null, emptyMap(), "abgebrochen")
                }
                stand?.invoke(nummer + 1, seitenAnzahl)
                val seite = renderer.openPage(nummer)
                // Breite 1200 Pixel: genug für die Erkennung, deutlich weniger Speicher.
                // A4 braucht für Tesseract etwa 2000 Pixel Breite (rund 170 dpi);
                // mit 1000 Pixeln wurde oft gar kein Text erkannt (Julius' Bericht:
                // „nichts wurde geschwärzt", 24.11.2026).
                val breite = 2000
                val hoehe = (breite.toFloat() * seite.height / seite.width).toInt()
                // PdfRenderer verlangt ARGB_8888 — mit RGB_565 kommt
                // „unsupported pixel format" (Julius' Meldung vom 24.11.2026).
                val roh = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
                Canvas(roh).drawColor(Color.WHITE)
                seite.render(roh, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                seite.close()
                // ARGB_8888 bleiben: Tesseract (tess-two) kann nur dieses Format lesen —
                // mit RGB_565 kam "failed to read bitmap" (24.11.2026). Gespart wird
                // stattdessen über die Seitenbreite (1000 Pixel) und frühes Freigeben.
                val bild = roh

                // "nur_rahmen": nur der gezogene Bereich, keine Textregeln — sonst
                // schwärzt die App zusätzlich Text, obwohl nur der Rahmen gewählt wurde.
                val textModus = if (modus == "nur_rahmen") "keiner" else modus
                var ergebnis = schwaerzen(bild, erkennung, textModus)
                // Nachbesserung: keine Wörter gefunden → größer rendern und noch einmal.
                if (ergebnis.woerter == 0 && nummer == 0) {
                    val groesser = Bitmap.createScaledBitmap(bild, bild.width * 13 / 10,
                                                            bild.height * 13 / 10, true)
                    val zweiter = schwaerzen(groesser, erkennung, modus)
                    groesser.recycle()
                    if (zweiter.woerter > 0) ergebnis = zweiter
                }
                val geschwaerzt = ergebnis.bild
                // Gezogener Rahmen (0..1) gilt für jede Seite — etwa für einen QR-Code.
                if (bereich != null) {
                    val z = Canvas(geschwaerzt)
                    z.drawRect(bereich.left * geschwaerzt.width, bereich.top * geschwaerzt.height,
                               bereich.right * geschwaerzt.width, bereich.bottom * geschwaerzt.height,
                               android.graphics.Paint().apply { color = Color.BLACK })
                    gesamt["bereich"] = (gesamt["bereich"] ?: 0) + 1
                }
                val zaehler = ergebnis.zaehler
                woerterGesamt += ergebnis.woerter
                bild.recycle()
                for ((art, anzahl) in zaehler) {
                    gesamt[art] = (gesamt[art] ?: 0) + anzahl
                }

                val seitenInfo = PdfDocument.PageInfo.Builder(breite, hoehe, nummer + 1).create()
                val neueSeite = neuesPdf.startPage(seitenInfo)
                neueSeite.canvas.drawBitmap(geschwaerzt, 0f, 0f, null)
                neuesPdf.finishPage(neueSeite)
                geschwaerzt.recycle()
            }
        } finally {
            renderer.close()
            descriptor.close()
            zwischen.delete()
        }

        val ziel = speichere(context, neuesPdf, name)
        neuesPdf.close()
        return Ergebnis(seitenAnzahl, ziel, gesamt, "", woerterGesamt)
    }

    /**
     * Wendet die Formen aus dem Markier-Editor auf JEDE Seite an (25.11.2026).
     *
     * Anlaß: Julius bearbeitete ein PDF, drückte „Fertig" — und das Ergebnis wurde nie
     * geschrieben, weil die Formen am PDF-Weg vorbeigingen. Dieselbe Schleife wie oben,
     * nur ohne Texterkennung: es wird gezeichnet, was im Editor gezeichnet wurde.
     */
    /** Formen für ALLE Seiten (Rahmen-Weg: ein Rahmen gilt überall). */
    fun verarbeiteFormen(context: Context, uri: Uri, erkennung: Erkennung, name: String,
                         formen: List<Form>,
                         stand: ((Int, Int) -> Unit)? = null,
                         abgebrochen: (() -> Boolean)? = null): Ergebnis =
        formenVerarbeiten(context, uri, name, { formen }, stand, abgebrochen)

    /** Formen JE SEITE (Markier-Editor an einem mehrseitigen PDF).
     *
     * Anlaß (27.11.2026): Julius — „android lässt auch nicht im pdf blättern". Im Editor
     * gab es keine Seiten, und die Marken einer Seite landeten auf allen. Was man auf
     * Seite 2 malt, gehört auf Seite 2 — hier wird das angewendet.
     */
    fun verarbeiteFormenJeSeite(context: Context, uri: Uri, name: String,
                                jeSeite: Map<Int, List<Form>>,
                                stand: ((Int, Int) -> Unit)? = null,
                                abgebrochen: (() -> Boolean)? = null): Ergebnis =
        formenVerarbeiten(context, uri, name, { nummer -> jeSeite[nummer] ?: emptyList() },
                          stand, abgebrochen)

    private fun formenVerarbeiten(context: Context, uri: Uri, name: String,
                                  markenFuer: (Int) -> List<Form>,
                                  stand: ((Int, Int) -> Unit)?,
                                  abgebrochen: (() -> Boolean)?): Ergebnis {
        val zwischen = File(context.cacheDir, "quelle-formen.pdf")
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var neuesPdf: PdfDocument? = null
        return try {
            context.contentResolver.openInputStream(uri)?.use { quelle ->
                zwischen.outputStream().use { ziel -> quelle.copyTo(ziel) }
            } ?: return Ergebnis(0, null, emptyMap(), "PDF nicht lesbar")

            descriptor = ParcelFileDescriptor.open(zwischen, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            neuesPdf = PdfDocument()
            val seitenAnzahl = renderer.pageCount
            var abgebrochenBei = -1

            for (nummer in 0 until seitenAnzahl) {
                if (abgebrochen != null && abgebrochen()) {
                    abgebrochenBei = nummer
                    break
                }
                stand?.invoke(nummer + 1, seitenAnzahl)
                val seite = renderer.openPage(nummer)
                val breite = 2000
                val hoehe = (breite.toFloat() * seite.height / seite.width).toInt()
                val bild = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
                Canvas(bild).drawColor(Color.WHITE)
                seite.render(bild, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                seite.close()

                val marken = markenFuer(nummer)
                if (marken.isNotEmpty()) Formen.zeichne(bild, marken)

                val info = PdfDocument.PageInfo.Builder(breite, hoehe, nummer + 1).create()
                val neueSeite = neuesPdf.startPage(info)
                neueSeite.canvas.drawBitmap(bild, 0f, 0f, null)
                neuesPdf.finishPage(neueSeite)
                bild.recycle()
            }

            if (abgebrochenBei >= 0) {
                Ergebnis(abgebrochenBei, null, emptyMap(), "abgebrochen")
            } else {
                val ziel = speichere(context, neuesPdf, name)
                Ergebnis(seitenAnzahl, ziel, mapOf("formen" to formen.size), "")
            }
        } catch (fehler: Throwable) {
            Ergebnis(0, null, emptyMap(), "${fehler.javaClass.simpleName}: ${fehler.message}")
        } finally {
            try { renderer?.close() } catch (fehler: Throwable) {}
            try { descriptor?.close() } catch (fehler: Throwable) {}
            try { neuesPdf?.close() } catch (fehler: Throwable) {}
            zwischen.delete()
        }
    }

    private fun speichere(context: Context, dokument: PdfDocument, quellName: String): Uri? {
        val name = "$quellName-geschwaerzt.pdf"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val werte = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, werte)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { strom -> dokument.writeTo(strom) }
            }
            uri
        } else {
            val ordner = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val datei = File(ordner, name)
            FileOutputStream(datei).use { strom -> dokument.writeTo(strom) }
            Uri.fromFile(datei)
        }
    }
}
