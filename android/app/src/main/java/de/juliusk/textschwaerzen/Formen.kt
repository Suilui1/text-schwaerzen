package de.juliusk.textschwaerzen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import org.json.JSONArray

/** Eine Form aus dem Markier-Editor; Koordinaten 0…1 (seitenunabhängig). */
data class Form(
    val typ: String,
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val text: String = "",
    /** Schriftgröße als Anteil der Bildhöhe (0 = Standard 0,03). */
    val groesse: Float = 0f,
    /** Schriftstil: normal · fett · kursiv. */
    val stil: String = "normal",
    /** Strichdicke als Anteil der Bildhöhe (0 = Standard 0,006). */
    val dicke: Float = 0f,
    /** Pfeilrichtung bei typ = pfeil: rechts · links · beide · linie. */
    val richtung: String = "rechts"
)

object Formen {

    fun vonJson(roh: String?): List<Form> {
        if (roh.isNullOrBlank()) return emptyList()
        return try {
            val reihe = JSONArray(roh)
            (0 until reihe.length()).map { i ->
                val o = reihe.getJSONObject(i)
                Form(o.optString("typ", "schwaerzen"),
                     o.optDouble("x0", 0.0).toFloat(), o.optDouble("y0", 0.0).toFloat(),
                     o.optDouble("x1", 0.0).toFloat(), o.optDouble("y1", 0.0).toFloat(),
                     o.optString("text", ""),
                     o.optDouble("groesse", 0.0).toFloat(),
                     o.optString("stil", "normal"),
                     o.optDouble("dicke", 0.0).toFloat(),
                     o.optString("richtung", "rechts"))
            }
        } catch (fehler: Throwable) {
            emptyList()
        }
    }

    fun ersteSchwaerzung(formen: List<Form>): Form? = formen.firstOrNull { it.typ == "schwaerzen" }

    /** Schriftart zum Stil. */
    fun schrift(stil: String): Typeface = when (stil) {
        "fett" -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        "kursiv" -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        "fettkursiv" -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        else -> Typeface.DEFAULT
    }

    /** Dreieck-Spitze; Richtung ergibt sich aus (x0,y0) → (x1,y1). */
    fun spitze(x0: Float, y0: Float, x1: Float, y1: Float, laenge: Float): Path {
        val winkel = Math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble())
        val links = winkel + Math.PI * 0.85
        val rechts = winkel - Math.PI * 0.85
        return Path().apply {
            moveTo(x1, y1)
            lineTo(x1 + (laenge * Math.cos(links)).toFloat(),
                   y1 + (laenge * Math.sin(links)).toFloat())
            lineTo(x1 + (laenge * Math.cos(rechts)).toFloat(),
                   y1 + (laenge * Math.sin(rechts)).toFloat())
            close()
        }
    }

    /** Zeichnet alle Formen auf das Bild (verändert die übergebene Kopie). */
    fun zeichne(bild: Bitmap, formen: List<Form>) {
        val z = Canvas(bild)
        val hoehe = bild.height.toFloat()
        for (f in formen) {
            val links = f.x0 * bild.width
            val oben = f.y0 * bild.height
            val rechts = f.x1 * bild.width
            val unten = f.y1 * bild.height
            val dicke = (if (f.dicke > 0f) f.dicke else 0.006f) * hoehe
            when (f.typ) {
                "schwaerzen" -> z.drawRect(links, oben, rechts, unten,
                    Paint().apply { color = Color.BLACK })
                "hervorheben" -> z.drawRect(links, oben, rechts, unten,
                    Paint().apply { color = Color.argb(120, 255, 235, 0) })
                "unterstreichen" -> z.drawRect(links, unten - dicke * 1.5f, rechts, unten,
                    Paint().apply { color = Color.BLACK })
                "durchstreichen" -> z.drawRect(links, (oben + unten) / 2f - dicke, rechts,
                    (oben + unten) / 2f + dicke, Paint().apply { color = Color.BLACK })
                "pfeil" -> {
                    val stift = Paint().apply {
                        color = Color.BLACK; strokeWidth = dicke; style = Paint.Style.STROKE
                    }
                    z.drawLine(links, oben, rechts, unten, stift)
                    val voll = Paint().apply { color = Color.BLACK }
                    if (f.richtung == "rechts" || f.richtung == "beide") {
                        z.drawPath(spitze(links, oben, rechts, unten, dicke * 6f), voll)
                    }
                    if (f.richtung == "links" || f.richtung == "beide") {
                        z.drawPath(spitze(rechts, unten, links, oben, dicke * 6f), voll)
                    }
                }
                "text" -> {
                    val groesse = (if (f.groesse > 0f) f.groesse else 0.03f) * hoehe
                    z.drawText(f.text.ifBlank { "Text" }, links, unten - 6f,
                        Paint().apply {
                            color = Color.BLACK
                            textSize = groesse
                            typeface = schrift(f.stil)
                        })
                }
                "stempel" -> {
                    val groesse = (unten - oben).coerceAtLeast(30f)
                    z.drawRect(links, oben, rechts, unten,
                        Paint().apply { color = Color.argb(210, 255, 255, 255) })
                    z.drawRect(links, oben, rechts, unten,
                        Paint().apply {
                            color = Color.RED; style = Paint.Style.STROKE
                            strokeWidth = dicke
                        })
                    z.drawText(f.text.ifBlank { "GEPRÜFT" }, links + 8f,
                        (oben + unten) / 2f + groesse / 4f,
                        Paint().apply {
                            color = Color.RED
                            textSize = groesse * 0.6f
                            typeface = schrift(f.stil)
                        })
                }
                "verwischen" -> {
                    val breite = (rechts - links).toInt().coerceAtLeast(1)
                    val h = (unten - oben).toInt().coerceAtLeast(1)
                    if (links >= 0 && oben >= 0 && rechts <= bild.width && unten <= bild.height) {
                        val klein = Bitmap.createScaledBitmap(
                            Bitmap.createBitmap(bild, links.toInt(), oben.toInt(), breite, h),
                            (breite / 12).coerceAtLeast(1), (h / 12).coerceAtLeast(1), false)
                        z.drawBitmap(klein, null,
                            android.graphics.RectF(links, oben, rechts, unten), null)
                        klein.recycle()
                    }
                }
            }
        }
    }
}
