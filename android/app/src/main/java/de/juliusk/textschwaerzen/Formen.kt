package de.juliusk.textschwaerzen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.json.JSONArray

/** Eine Form aus dem Markier-Editor; Koordinaten 0…1 (seitenunabhängig). */
data class Form(
    val typ: String,
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val text: String = ""
)

object Formen {

    /** Dreieck an einem Ende; Richtung ergibt sich aus (x0,y0) → (x1,y1). */
    fun vorschauSpitze(zx: Float, zy: Float, vonX: Float, vonY: Float): Path = spitze(vonX, vonY, zx, zy, 30f)

    private fun spitze(x0: Float, y0: Float, x1: Float, y1: Float, laenge: Float): Path {
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


    fun vonJson(roh: String?): List<Form> {
        if (roh.isNullOrBlank()) return emptyList()
        return try {
            val reihe = JSONArray(roh)
            (0 until reihe.length()).map { i ->
                val o = reihe.getJSONObject(i)
                Form(o.optString("typ", "schwaerzen"),
                     o.optDouble("x0", 0.0).toFloat(), o.optDouble("y0", 0.0).toFloat(),
                     o.optDouble("x1", 0.0).toFloat(), o.optDouble("y1", 0.0).toFloat(),
                     o.optString("text", ""))
            }
        } catch (fehler: Throwable) {
            emptyList()
        }
    }

    fun ersteSchwaerzung(formen: List<Form>): Form? = formen.firstOrNull { it.typ == "schwaerzen" }

    /** Zeichnet alle Formen auf das Bild (verändert die übergebene Kopie). */
    fun zeichne(bild: Bitmap, formen: List<Form>) {
        val z = Canvas(bild)
        for (f in formen) {
            val links = f.x0 * bild.width
            val oben = f.y0 * bild.height
            val rechts = f.x1 * bild.width
            val unten = f.y1 * bild.height
            when (f.typ) {
                "schwaerzen" -> z.drawRect(links, oben, rechts, unten,
                    Paint().apply { color = Color.BLACK })
                "hervorheben" -> z.drawRect(links, oben, rechts, unten,
                    Paint().apply { color = Color.argb(120, 255, 235, 0) })
                "unterstreichen" -> z.drawRect(links, unten - 4f, rechts, unten,
                    Paint().apply { color = Color.BLACK })
                "durchstreichen" -> z.drawRect(links, (oben + unten) / 2f - 3f, rechts,
                    (oben + unten) / 2f + 3f, Paint().apply { color = Color.BLACK })
                "pfeil", "pfeil_links", "pfeil_beide", "linie" -> {
                    val stift = Paint().apply {
                        color = Color.BLACK; strokeWidth = 6f; style = Paint.Style.STROKE
                    }
                    z.drawLine(links, oben, rechts, unten, stift)
                    val spitze = Paint().apply { color = Color.BLACK }
                    if (f.typ == "pfeil" || f.typ == "pfeil_beide") {
                        z.drawPath(spitze(rechts, unten, rechts, unten, 30f), spitze)
                    }
                    if (f.typ == "pfeil_links" || f.typ == "pfeil_beide") {
                        z.drawPath(spitze(links, oben, links, oben, 30f), spitze)
                    }
                }
                "text" -> z.drawText(f.text.ifBlank { "Text" }, links, unten - 6f,
                    Paint().apply { color = Color.BLACK; textSize = bild.height * 0.03f })
                "stempel" -> {
                    val groesse = (unten - oben).coerceAtLeast(30f)
                    z.drawRect(links, oben, rechts, unten,
                        Paint().apply { color = Color.argb(210, 255, 255, 255) })
                    z.drawRect(links, oben, rechts, unten,
                        Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 4f })
                    z.drawText(f.text.ifBlank { "GEPRÜFT" }, links + 8f, (oben + unten) / 2f + groesse / 4f,
                        Paint().apply { color = Color.RED; textSize = groesse * 0.6f })
                }
                "verwischen" -> {
                    // Wirklich unleserlich: den Ausschnitt verkleinern und vergrößert zurückzeichnen
                    val breite = (rechts - links).toInt().coerceAtLeast(1)
                    val hoehe = (unten - oben).toInt().coerceAtLeast(1)
                    if (links >= 0 && oben >= 0 && rechts <= bild.width && unten <= bild.height) {
                        val klein = Bitmap.createScaledBitmap(
                            Bitmap.createBitmap(bild, links.toInt(), oben.toInt(), breite, hoehe),
                            (breite / 12).coerceAtLeast(1), (hoehe / 12).coerceAtLeast(1), false)
                        z.drawBitmap(klein, null,
                            android.graphics.RectF(links, oben, rechts, unten), null)
                        klein.recycle()
                    }
                }
            }
        }
    }
}
