package de.juliusk.textschwaerzen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Texterkennung auf dem Gerät — mit ML Kit (mitgeliefertes Modell, arbeitet offline).
 *
 * Warum nicht Tesseract (24.11.2026): auf Julius' Handy meldete tess-two beim Start
 * „init fehlgeschlagen", der Selbsttest fand 0 Wörter. ML Kit bringt sein Modell im
 * Programm mit und braucht weder Play-Dienste noch eine Sprachdatei.
 */
class Erkennung(private val context: android.content.Context) {

    /** Schriftsystem; ML Kit kennt genau diese fünf (Latein, Chinesisch, Japanisch,
     *  Koreanisch, Devanagari). Kyrillisch, Griechisch, Arabisch kann es nicht. */
    enum class Schrift(val anzeige: String) {
        LATEINISCH("Lateinisch (deutsch, englisch, romanische Sprachen)"),
        CHINESISCH("Chinesisch"),
        JAPANISCH("Japanisch"),
        KOREANISCH("Koreanisch"),
        DEVANAGARI("Devanagari (Hindi u.a.)")
    }

    var schrift: Schrift = Schrift.LATEINISCH

    private var erkennerAktuell: com.google.mlkit.vision.text.TextRecognizer? = null

    private fun erkenner(): com.google.mlkit.vision.text.TextRecognizer {
        val vorhanden = erkennerAktuell
        if (vorhanden != null) return vorhanden
        val neu = when (schrift) {
            Schrift.LATEINISCH -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            Schrift.CHINESISCH -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            Schrift.JAPANISCH -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            Schrift.KOREANISCH -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            Schrift.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }
        erkennerAktuell = neu
        return neu
    }

    /** Schrift wechseln: der alte Erkenner wird freigegeben. */
    fun setzeSchrift(neu: Schrift) {
        if (neu == schrift && erkennerAktuell != null) return
        erkennerAktuell?.close()
        erkennerAktuell = null
        schrift = neu
    }

    var zustand: String = "bereit (ML Kit, Modell im Programm)"
        private set

    data class Wort(val text: String, val links: Int, val oben: Int, val rechts: Int, val unten: Int)

    private val codeErkenner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128,
                                   Barcode.FORMAT_EAN_13, Barcode.FORMAT_CODE_39,
                                   Barcode.FORMAT_PDF417, Barcode.FORMAT_DATA_MATRIX)
                .build()
        )
    }

    /** QR- und Strichcodes: Kästen, die mitgeschwärzt werden müssen. */
    fun barcodeScannen(bild: Bitmap): List<android.graphics.Rect> {
        val eingabe = InputImage.fromBitmap(bild, 0)
        // process() liefert direkt eine Liste von Barcode-Objekten (kein .barcodes-Feld)
        val treffer: List<Barcode> = try {
            Tasks.await(codeErkenner.process(eingabe), 60, TimeUnit.SECONDS)
        } catch (fehler: Throwable) {
            return emptyList()
        }
        return treffer.mapNotNull { it.boundingBox }
    }

    /** Alle Wörter eines Bildes mit ihrer Lage. */
    fun woerter(bild: Bitmap): List<Wort> {
        val eingabe = InputImage.fromBitmap(bild, 0)
        val ergebnis = try {
            Tasks.await(erkenner().process(eingabe), 60, TimeUnit.SECONDS)
        } catch (fehler: Throwable) {
            zustand = "Fehler: " + fehler.javaClass.simpleName + ": " + fehler.message
            return emptyList()
        }
        val liste = mutableListOf<Wort>()
        for (block in ergebnis.textBlocks) {
            for (zeile in block.lines) {
                for (element in zeile.elements) {
                    val kasten = element.boundingBox ?: continue
                    val text = element.text.trim()
                    if (text.isNotEmpty()) {
                        liste.add(Wort(text, kasten.left, kasten.top, kasten.right, kasten.bottom))
                    }
                }
            }
        }
        zustand = "bereit (ML Kit) · ${liste.size} Wörter"
        return liste
    }

    fun schliessen() {
        erkennerAktuell?.close()
        erkennerAktuell = null
    }
}

data class SchwaeerzErgebnis(val bild: Bitmap, val zaehler: Map<String, Int>, val woerter: Int)

/** Schwärzt je nach Modus: passende Zeilen oder alle. */
fun schwaerzen(bild: Bitmap, erkennung: Erkennung, modus: String): SchwaeerzErgebnis {
    val kopie = bild.copy(Bitmap.Config.ARGB_8888, true)
    val zeichner = Canvas(kopie)
    val stift = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }

    val woerter = erkennung.woerter(bild)
    val zaehler = mutableMapOf<String, Int>()

    // Wörter zu Zeilen bündeln (ähnliche Oberkante) — die Regeln arbeiten auf Zeilen.
    val zeilen = mutableListOf<MutableList<Erkennung.Wort>>()
    for (wort in woerter) {
        val passende = zeilen.firstOrNull { zeile ->
            val erstes = zeile.first()
            Math.abs(erstes.oben - wort.oben) < (erstes.unten - erstes.oben + 4) / 2
        }
        if (passende != null) passende.add(wort) else zeilen.add(mutableListOf(wort))
    }

    // Codes immer mitschwaerzen — sie enthalten dieselben Daten als Bild.
    var codeZahl = 0
    for (kasten in erkennung.barcodeScannen(bild)) {
        zeichner.drawRect((kasten.left - 4).toFloat(), (kasten.top - 4).toFloat(),
                          (kasten.right + 4).toFloat(), (kasten.bottom + 4).toFloat(), stift)
        codeZahl++
    }
    if (codeZahl > 0) zaehler["barcode"] = codeZahl

    for (zeile in zeilen) {
        val text = zeile.joinToString(" ") { it.text }
        val arten = Regeln.treffer(text, modus)
        if (arten.isEmpty()) continue
        for (wort in zeile) {
            zeichner.drawRect(
                (wort.links - 2).toFloat(), (wort.oben - 4).toFloat(),
                (wort.rechts + 2).toFloat(), (wort.unten + 4).toFloat(), stift
            )
        }
        for (art in arten) zaehler[art] = (zaehler[art] ?: 0) + 1
    }
    return SchwaeerzErgebnis(kopie, zaehler, woerter.size)
}
