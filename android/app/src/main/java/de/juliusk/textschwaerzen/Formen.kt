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
    var x0: Float, var y0: Float, var x1: Float, var y1: Float,
    val text: String = "",
    /** Schriftgröße als Anteil der Bildhöhe (0 = Standard 0,03). */
    val groesse: Float = 0f,
    /** Schriftstil: normal · fett · kursiv. */
    val stil: String = "normal",
    /** Strichdicke als Anteil der Bildhöhe (0 = Standard 0,006). */
    val dicke: Float = 0f,
    /** Pfeilrichtung bei typ = pfeil: rechts · links · beide · linie. */
    val richtung: String = "rechts",
    /** Farbe für Text, Pfeil und Stempelinhalt (ARGB). */
    val farbe: Int = android.graphics.Color.BLACK,
    /** Rahmenfarbe des Stempels (ARGB). */
    val rahmenFarbe: Int = android.graphics.Color.RED
)

object Formen {

    /** Eine Form als JSON — Gegenstück zu [vonJson]. */
    fun alsJson(f: Form): org.json.JSONObject = org.json.JSONObject().apply {
        put("typ", f.typ)
        put("x0", f.x0.toDouble()); put("y0", f.y0.toDouble())
        put("x1", f.x1.toDouble()); put("y1", f.y1.toDouble())
        put("text", f.text)
        put("groesse", f.groesse.toDouble())
        put("stil", f.stil)
        put("dicke", f.dicke.toDouble())
        put("richtung", f.richtung)
        put("farbe", f.farbe)
        put("rahmenFarbe", f.rahmenFarbe)
    }

    /** Formen je Seite aus JSON: {"0":[...],"1":[...]} → Map. */
    fun jeSeiteVonJson(roh: String?): Map<Int, List<Form>> {
        if (roh.isNullOrBlank()) return emptyMap()
        return try {
            val ganz = org.json.JSONObject(roh)
            ganz.keys().asSequence().mapNotNull { schluessel ->
                val nummer = schluessel.toIntOrNull() ?: return@mapNotNull null
                nummer to vonJson(ganz.optJSONArray(schluessel)?.toString())
            }.toMap()
        } catch (fehler: Throwable) {
            emptyMap()
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
                     o.optString("text", ""),
                     o.optDouble("groesse", 0.0).toFloat(),
                     o.optString("stil", "normal"),
                     o.optDouble("dicke", 0.0).toFloat(),
                     o.optString("richtung", "rechts"),
                     o.optInt("farbe", android.graphics.Color.BLACK),
                     o.optInt("rahmenFarbe", android.graphics.Color.RED))
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
    /** Weichzeichnen: zwei Durchgänge Kastenfilter, kommt einem Gauß-Filter nahe.
     *
     * Warum (26.11.2026): Julius — „verwischen nicht so schön". Gemeint war das Ergebnis
     * auf Android: es verpixelte (Blöcke 12), das sieht hart aus. Auf dem Mac ist es ein
     * echter Weichzeichner, und das gefiel ihm. Deshalb hier dasselbe: Präfixsummen je
     * Zeile und Spalte, kein zusätzliches Paket.
     */
    fun weichzeichnen(quelle: Bitmap, radius: Int): Bitmap {
        val w = quelle.width
        val h = quelle.height
        if (radius < 1 || w < 3 || h < 3) return quelle
        val ein = IntArray(w * h)
        quelle.getPixels(ein, 0, w, 0, 0, w, h)
        val quer = IntArray(w * h)
        val aus = IntArray(w * h)
        val gross = maxOf(w, h) + 1
        val s0 = IntArray(gross)
        val s1 = IntArray(gross)
        val s2 = IntArray(gross)
        val s3 = IntArray(gross)

        // waagerecht
        for (y in 0 until h) {
            val start = y * w
            s0[0] = 0; s1[0] = 0; s2[0] = 0; s3[0] = 0
            for (x in 0 until w) {
                val p = ein[start + x]
                s0[x + 1] = s0[x] + ((p ushr 24) and 0xFF)
                s1[x + 1] = s1[x] + ((p ushr 16) and 0xFF)
                s2[x + 1] = s2[x] + ((p ushr 8) and 0xFF)
                s3[x + 1] = s3[x] + (p and 0xFF)
            }
            for (x in 0 until w) {
                val lo = maxOf(0, x - radius)
                val hi = minOf(w - 1, x + radius)
                val n = hi - lo + 1
                quer[start + x] = (((s0[hi + 1] - s0[lo]) / n) shl 24) or
                        (((s1[hi + 1] - s1[lo]) / n) shl 16) or
                        (((s2[hi + 1] - s2[lo]) / n) shl 8) or
                        ((s3[hi + 1] - s3[lo]) / n)
            }
        }
        // senkrecht
        for (x in 0 until w) {
            s0[0] = 0; s1[0] = 0; s2[0] = 0; s3[0] = 0
            for (y in 0 until h) {
                val p = quer[y * w + x]
                s0[y + 1] = s0[y] + ((p ushr 24) and 0xFF)
                s1[y + 1] = s1[y] + ((p ushr 16) and 0xFF)
                s2[y + 1] = s2[y] + ((p ushr 8) and 0xFF)
                s3[y + 1] = s3[y] + (p and 0xFF)
            }
            for (y in 0 until h) {
                val lo = maxOf(0, y - radius)
                val hi = minOf(h - 1, y + radius)
                val n = hi - lo + 1
                aus[y * w + x] = (((s0[hi + 1] - s0[lo]) / n) shl 24) or
                        (((s1[hi + 1] - s1[lo]) / n) shl 16) or
                        (((s2[hi + 1] - s2[lo]) / n) shl 8) or
                        ((s3[hi + 1] - s3[lo]) / n)
            }
        }
        val fertig = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        fertig.setPixels(aus, 0, w, 0, 0, w, h)
        return fertig
    }

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
                    // Wichtig: Start- und Endpunkt getrennt behandeln. Vorher wurden die
                    // Koordinaten zu einem Rechteck sortiert — dadurch zeigte der Pfeil
                    // immer nach rechts unten, egal wie gezogen wurde (25.11.2026).
                    val sx = f.x0 * bild.width
                    val sy = f.y0 * bild.height
                    val ex = f.x1 * bild.width
                    val ey = f.y1 * bild.height
                    val stift = Paint().apply {
                        color = f.farbe; strokeWidth = dicke; style = Paint.Style.STROKE
                    }
                    z.drawLine(sx, sy, ex, ey, stift)
                    val voll = Paint().apply { color = f.farbe }
                    if (f.richtung == "rechts" || f.richtung == "beide") {
                        z.drawPath(spitze(sx, sy, ex, ey, dicke * 6f), voll)
                    }
                    if (f.richtung == "links" || f.richtung == "beide") {
                        z.drawPath(spitze(ex, ey, sx, sy, dicke * 6f), voll)
                    }
                }
                "text" -> {
                    val groesse = (if (f.groesse > 0f) f.groesse else 0.03f) * hoehe
                    z.drawText(f.text.ifBlank { "Text" }, links, unten - 6f,
                        Paint().apply {
                            color = f.farbe
                            textSize = groesse
                            typeface = schrift(f.stil)
                        })
                }
                "stempel" -> {
                    val groesse = if (f.groesse > 0f) f.groesse * hoehe
                                  else (unten - oben).coerceAtLeast(30f)
                    z.drawRect(links, oben, rechts, unten,
                        Paint().apply { color = Color.argb(210, 255, 255, 255) })
                    z.drawRect(links, oben, rechts, unten,
                        Paint().apply {
                            color = f.rahmenFarbe; style = Paint.Style.STROKE
                            strokeWidth = dicke
                        })
                    z.drawText(f.text.ifBlank { "GEPRÜFT" }, links + 8f,
                        (oben + unten) / 2f + groesse / 4f,
                        Paint().apply {
                            color = f.farbe
                            textSize = groesse * 0.6f
                            typeface = schrift(f.stil)
                        })
                }
                "verwischen" -> {
                    val breite = (rechts - links).toInt().coerceAtLeast(1)
                    val h = (unten - oben).toInt().coerceAtLeast(1)
                    if (links >= 0 && oben >= 0 && rechts <= bild.width && unten <= bild.height) {
                        val ausschnitt = Bitmap.createBitmap(bild, links.toInt(), oben.toInt(), breite, h)
                        // Radius wie auf dem Mac: ein Achtel der kurzen Seite, mindestens 6 Punkte
                        val radius = maxOf(6, minOf(breite, h) / 8)
                        val weich = weichzeichnen(ausschnitt, radius)
                        z.drawBitmap(weich, null, android.graphics.RectF(links, oben, rechts, unten), null)
                        weich.recycle()
                        ausschnitt.recycle()
                    }
                }
            }
        }
    }
}
