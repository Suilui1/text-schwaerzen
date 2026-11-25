package de.juliusk.textschwaerzen

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Markier-Editor mit den Werkzeugen, die Whiteout anbietet:
 * Schwärzen, Hervorheben, Unterstreichen, Durchstreichen, Pfeil, Text, Stempel, Verwischen.
 *
 * Julius' Wunsch (25.11.2026): „ergänze die Whiteout-Funktionen". Auf dem Rechner gab es
 * sie schon, auf Android nur einen einzelnen Rahmen.
 *
 * Rückgabe: JSON-Liste der Formen, Koordinaten 0…1 (seitenunabhängig).
 */
class MarkierActivity : Activity() {

    companion object {
        var uebergebenesBild: Bitmap? = null
    }

    private lateinit var bild: Bitmap
    private lateinit var ansicht: MarkierAnsicht
    private var werkzeug = "schwaerzen"
    private var stempel = "GEPRÜFT"
    private val werkzeuge = listOf(
        "schwaerzen" to "Schwärzen",
        "hervorheben" to "Hervorheben",
        "unterstreichen" to "Unterstreichen",
        "durchstreichen" to "Durchstreichen",
        "pfeil" to "Pfeil",
        "text" to "Text",
        "stempel" to "Stempel",
        "verwischen" to "Verwischen"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val direkt = uebergebenesBild
        uebergebenesBild = null
        val datei = intent.getStringExtra("datei")
        val geladen = direkt ?: try {
            if (datei == null) null else BitmapFactory.decodeFile(datei)
        } catch (fehler: Throwable) {
            null
        }
        if (geladen == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        bild = geladen
        ansicht = MarkierAnsicht(this, bild)

        val rahmen = FrameLayout(this)
        rahmen.addView(ansicht, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Werkzeugleiste oben
        val leiste = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val rollend = HorizontalScrollView(this).apply { addView(leiste) }
        for ((wert, anzeige) in werkzeuge) {
            val knopf = Button(this).apply {
                text = anzeige
                setOnClickListener {
                    if (wert == "stempel") stempelWaehlen() else if (wert == "text") {
                        hinweisText()
                    }
                    werkzeug = wert
                    ansicht.setzeWerkzeug(wert)
                    if (wert == "stempel") ansicht.setzeStempel(stempel)
                    berichtAnzeige.text = "Werkzeug: " + anzeige + beschreibung(wert)
                }
            }
            leiste.addView(knopf)
        }
        rahmen.addView(rollend, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP))

        berichtAnzeige = TextView(this).apply {
            text = "Werkzeug oben wählen, dann im Bild ziehen. Schwärzen ist voreingestellt."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            setPadding(20, 10, 20, 10)
        }
        val berichtRahmen = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP)
        berichtRahmen.topMargin = 120
        rahmen.addView(berichtAnzeige, berichtRahmen)

        // Knöpfe unten
        val unten = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        unten.addView(Button(this).apply {
            text = "Rückgängig"
            setOnClickListener { ansicht.rueckgaengig() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        unten.addView(Button(this).apply {
            text = "Abbrechen"
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        unten.addView(Button(this).apply {
            text = "Fertig"
            setOnClickListener { fertig() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        rahmen.addView(unten, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM))

        setContentView(rahmen)
    }

    private lateinit var berichtAnzeige: TextView

    private fun beschreibung(wert: String) = when (wert) {
        "schwaerzen" -> " — Bereich wird schwarz."
        "hervorheben" -> " — Bereich wird gelb."
        "unterstreichen" -> " — Linie unter dem Bereich."
        "durchstreichen" -> " — Linie durch die Mitte."
        "pfeil" -> " — Pfeil von links oben nach rechts unten."
        "text" -> " — Text auf den Bereich."
        "stempel" -> " — Stempeltext im Kasten."
        "verwischen" -> " — Bereich wird grob (unleserlich)."
        else -> ""
    }

    private fun stempelWaehlen() {
        val liste = arrayOf("GEPRÜFT", "VERTRAULICH", "KOPIE", "ENTWURF", "UNGÜLTIG", "BEZAHLT", "ERLEDIGT")
        AlertDialog.Builder(this)
            .setTitle("Stempel")
            .setItems(liste) { _, welcher -> stempel = liste[welcher] }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun hinweisText() {
        val feld = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Text")
            .setView(feld)
            .setPositiveButton("OK") { _, _ -> ansicht.naechsterText = feld.text.toString() }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun fertig() {
        val formen = ansicht.formen
        if (formen.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val reihe = org.json.JSONArray()
        for (f in formen) {
            val o = org.json.JSONObject()
            o.put("typ", f.typ)
            o.put("x0", f.x0.toDouble()); o.put("y0", f.y0.toDouble())
            o.put("x1", f.x1.toDouble()); o.put("y1", f.y1.toDouble())
            o.put("text", f.text)
            reihe.put(o)
        }
        val daten = Intent()
        daten.putExtra("formen", reihe.toString())
        // Rückwärtsverträglich: erster Schwärzungsrahmen als x0..y1 (für den PDF-Weg)
        formen.firstOrNull { it.typ == "schwaerzen" }?.let {
            daten.putExtra("x0", it.x0); daten.putExtra("y0", it.y0)
            daten.putExtra("x1", it.x1); daten.putExtra("y1", it.y1)
        }
        setResult(RESULT_OK, daten)
        finish()
    }
}

/** Zeigt das Bild, nimmt Formen auf und zeichnet sie sofort. */
class MarkierAnsicht(kontext: android.content.Context, private val bild: Bitmap) : View(kontext) {

    val formen = mutableListOf<Form>()
    var naechsterText: String = ""

    private var startX = 0f
    private var startY = 0f
    private var aktuell: RectF? = null
    private var werkzeug = "schwaerzen"
    private var stempelText = "GEPRÜFT"

    fun setzeWerkzeug(wert: String) { werkzeug = wert }
    fun setzeStempel(wert: String) { stempelText = wert }

    fun rueckgaengig() {
        if (formen.isNotEmpty()) {
            formen.removeAt(formen.size - 1)
            invalidate()
        }
    }

    private fun skala(): Float = minOf(width.toFloat() / bild.width, height.toFloat() / bild.height)

    private fun ziel(): RectF {
        val s = skala()
        val b = RectF(0f, 0f, bild.width * s, bild.height * s)
        b.offset((width - b.width()) / 2f, (height - b.height()) / 2f)
        return b
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val z = ziel()
        canvas.drawBitmap(bild, null, z, null)
        for (f in formen) zeichne(canvas, f, z, skala())
        aktuell?.let {
            val stift = Paint().apply { color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 4f }
            canvas.drawRect(it, stift)
        }
    }

    private fun zeichne(canvas: Canvas, f: Form, z: RectF, s: Float) {
        val links = z.left + f.x0 * z.width()
        val oben = z.top + f.y0 * z.height()
        val rechts = z.left + f.x1 * z.width()
        val unten = z.top + f.y1 * z.height()
        when (f.typ) {
            "schwaerzen" -> canvas.drawRect(links, oben, rechts, unten,
                Paint().apply { color = Color.BLACK })
            "hervorheben" -> canvas.drawRect(links, oben, rechts, unten,
                Paint().apply { color = Color.argb(120, 255, 235, 0) })
            "unterstreichen" -> canvas.drawRect(links, unten - 3f, rechts, unten,
                Paint().apply { color = Color.BLACK })
            "durchstreichen" -> canvas.drawRect(links, (oben + unten) / 2f - 2f, rechts,
                (oben + unten) / 2f + 2f, Paint().apply { color = Color.BLACK })
            "pfeil" -> {
                val stift = Paint().apply {
                    color = Color.BLACK; strokeWidth = 4f; style = Paint.Style.STROKE
                }
                canvas.drawLine(links, oben, rechts, unten, stift)
                val pfeil = Path().apply {
                    moveTo(rechts, unten)
                    lineTo(rechts - 24f, unten - 6f)
                    lineTo(rechts - 6f, unten - 24f)
                    close()
                }
                canvas.drawPath(pfeil, Paint().apply { color = Color.BLACK })
            }
            "text" -> canvas.drawText(f.text.ifBlank { "Text" }, links, unten - 4f,
                Paint().apply { color = Color.BLACK; textSize = 34f })
            "stempel" -> {
                canvas.drawRect(links, oben, rechts, unten,
                    Paint().apply { color = Color.argb(200, 255, 255, 255) })
                canvas.drawRect(links, oben, rechts, unten,
                    Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f })
                canvas.drawText(f.text.ifBlank { "GEPRÜFT" }, links + 8f, (oben + unten) / 2f + 10f,
                    Paint().apply { color = Color.RED; textSize = 30f })
            }
            "verwischen" -> {
                // Vorschau: grobe Kästchen; die echte Unleserlichkeit entsteht beim Anwenden
                val stift = Paint().apply { color = Color.argb(160, 120, 120, 120) }
                var x = links
                while (x < rechts) {
                    var y = oben
                    while (y < unten) {
                        canvas.drawRect(x, y, minOf(x + 8f, rechts), minOf(y + 8f, unten), stift)
                        y += 8f
                    }
                    x += 8f
                }
            }
        }
    }

    override fun onTouchEvent(ereignis: MotionEvent): Boolean {
        when (ereignis.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ereignis.x; startY = ereignis.y
                aktuell = RectF(startX, startY, startX, startY)
            }
            MotionEvent.ACTION_MOVE -> {
                aktuell = RectF(minOf(startX, ereignis.x), minOf(startY, ereignis.y),
                                maxOf(startX, ereignis.x), maxOf(startY, ereignis.y))
            }
            MotionEvent.ACTION_UP -> {
                val r = aktuell
                aktuell = null
                if (r != null && r.width() > 12 && r.height() > 8) {
                    val z = ziel()
                    fun nx(x: Float) = ((x - z.left) / z.width()).coerceIn(0f, 1f)
                    fun ny(y: Float) = ((y - z.top) / z.height()).coerceIn(0f, 1f)
                    val text = when (werkzeug) {
                        "text" -> naechsterText.ifBlank { "Text" }
                        "stempel" -> stempelText
                        else -> ""
                    }
                    formen.add(Form(werkzeug, nx(r.left), ny(r.top),
                                                    nx(r.right), ny(r.bottom), text))
                    invalidate()
                }
            }
        }
        invalidate()
        return true
    }
}
