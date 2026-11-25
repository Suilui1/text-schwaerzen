package de.juliusk.textschwaerzen

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView

/**
 * Markier-Editor mit den Werkzeugen aus Whiteout.
 *
 * Julius' Wünsche vom 25.11.2026 nach und nach eingebaut:
 *   · acht Werkzeuge (Schwärzen, Hervorheben, Unter-, Durchstreichen, Pfeil, Text,
 *     Stempel, Verwischen)
 *   · Rückgängig
 *   · Text mit Größe und Stil (normal · fett · kursiv)
 *   · Pfeil mit Richtung und Dicke — als EIN Knopf mit Auswahl, nicht vier Knöpfe
 *
 * Rückgabe: JSON-Liste der Formen, Koordinaten 0…1.
 */
class MarkierActivity : Activity() {

    companion object {
        var uebergebenesBild: Bitmap? = null
    }

    private lateinit var bild: Bitmap
    private lateinit var ansicht: MarkierAnsicht
    private lateinit var bericht: TextView
    private var werkzeug = "schwaerzen"
    private var stempel = "GEPRÜFT"
    private var pfeilRichtung = "rechts"
    private var pfeilDicke = 0.006f
    private var stempelGroesse = 0.032f
    private var textFarbe = android.graphics.Color.BLACK
    private var pfeilFarbe = android.graphics.Color.BLACK
    private var stempelFarbe = android.graphics.Color.RED
    private var stempelRahmen = android.graphics.Color.RED

    private val werkzeuge = listOf(
        "schwaerzen" to "Schwärzen",
        "markieren" to "Markieren...",
        "pfeil" to "Pfeil…",
        "text" to "Text…",
        "stempel" to "Stempel…",
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
            setResult(RESULT_CANCELED); finish(); return
        }
        bild = geladen
        ansicht = MarkierAnsicht(this, bild)

        val rahmen = FrameLayout(this)
        rahmen.addView(ansicht, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val leiste = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((wert, anzeige) in werkzeuge) {
            leiste.addView(Button(this).apply {
                text = anzeige
                setOnClickListener {
                    when (wert) {
                        "pfeil" -> pfeilWaehlen()
                        "text" -> textStilWaehlen()
                        "stempel" -> stempelWaehlen()
                        "markieren" -> {
                            val arten = arrayOf("hervorheben", "unterstreichen", "durchstreichen")
                            val namen = arrayOf("Hervorheben (gelb)", "Unterstreichen",
                                                "Durchstreichen")
                            AlertDialog.Builder(this@MarkierActivity)
                                .setTitle("Markieren")
                                .setItems(namen) { _, welcher ->
                                    werkzeug = arten[welcher]
                                    ansicht.setzeWerkzeug(werkzeug)
                                    bericht.text = "Werkzeug: " + namen[welcher]
                                }
                                .setNegativeButton("Abbrechen", null)
                                .show()
                            return@setOnClickListener
                        }
                    }
                    werkzeug = wert
                    ansicht.setzeWerkzeug(wert)
                    bericht.text = "Werkzeug: " + anzeige + beschreibung(wert)
                }
            })
        }
        rahmen.addView(HorizontalScrollView(this).apply { addView(leiste) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP))

        bericht = TextView(this).apply {
            text = "Werkzeug oben wählen, dann im Bild ziehen."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            setPadding(20, 10, 20, 10)
        }
        val berichtRahmen = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP)
        berichtRahmen.topMargin = 130
        rahmen.addView(bericht, berichtRahmen)

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

    private fun beschreibung(wert: String) = when (wert) {
        "schwaerzen" -> " — Bereich wird schwarz."
        "hervorheben" -> " — Bereich wird gelb."
        "unterstreichen" -> " — Linie unter dem Bereich."
        "durchstreichen" -> " — Linie durch die Mitte."
        "pfeil" -> " — Richtung und Dicke wurden gewählt."
        "text" -> " — Größe und Stil wurden gewählt; Text kommt nach dem Ziehen."
        "stempel" -> " — Stempel: " + stempel
        "markieren" -> " — Hervorheben, Unterstreichen oder Durchstreichen wählen."
        "verwischen" -> " — Bereich wird unleserlich."
        else -> ""
    }

    /** Baut eine Farbauswahl (Reihe von Knöpfen) in den Dialog. */
    private fun farbReihe(stapel: LinearLayout, titel: String, vorwahl: Int,
                          beiWahl: (Int) -> Unit) {
        stapel.addView(TextView(this).apply { text = titel; setPadding(0, 16, 0, 0) })
        val reihe = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in Farben.WERTE.indices) {
            val farbe = Farben.WERTE[i]
            val knopf = Button(this).apply {
                text = ""
                setBackgroundColor(farbe)
                minWidth = 0
                minimumWidth = 0
                width = 70
                height = 70
                setOnClickListener {
                    beiWahl(farbe)
                    (parent as? LinearLayout)?.let { eltern ->
                        for (k in 0 until eltern.childCount) {
                            (eltern.getChildAt(k) as? Button)?.alpha = 0.45f
                        }
                    }
                    alpha = 1f
                }
            }
            knopf.alpha = if (farbe == vorwahl) 1f else 0.45f
            reihe.addView(knopf, LinearLayout.LayoutParams(0, 70, 1f))
        }
        stapel.addView(reihe)
        beiWahl(vorwahl)
    }

    /** Ein Knopf für den Pfeil: Richtung, Dicke und Farbe in einem Dialog. */
    private fun pfeilWaehlen() {
        val richtungen = arrayOf("rechts", "links", "beide", "linie")
        val anzeige = arrayOf("Pfeil nach rechts", "Pfeil nach links",
                              "Pfeil an beiden Enden", "nur Linie")
        val dicken = arrayOf("dünn", "mittel", "dick")
        val gewaehlt = intArrayOf(0, 1)

        val stapel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        stapel.addView(TextView(this).apply { text = "Richtung" })
        val richtungsWahl = RadioGroup(this)
        for (i in anzeige.indices) {
            richtungsWahl.addView(android.widget.RadioButton(this).apply {
                id = 500 + i
                text = anzeige[i]
                isChecked = i == 0
            })
        }
        stapel.addView(richtungsWahl)
        stapel.addView(TextView(this).apply { text = "Dicke"; setPadding(0, 16, 0, 0) })
        val dickenWahl = RadioGroup(this)
        for (i in dicken.indices) {
            dickenWahl.addView(android.widget.RadioButton(this).apply {
                id = 100 + i
                text = dicken[i]
                isChecked = i == 1
            })
        }
        stapel.addView(dickenWahl)
        farbReihe(stapel, "Farbe", pfeilFarbe) { pfeilFarbe = it }

        AlertDialog.Builder(this@MarkierActivity)
            .setTitle("Pfeil")
            .setView(stapel)
            .setPositiveButton("OK") { _, _ ->
                gewaehlt[0] = when (richtungsWahl.checkedRadioButtonId) {
                      500 -> 0; 501 -> 1; 502 -> 2; 503 -> 3; else -> 0
                }
                gewaehlt[1] = when (dickenWahl.checkedRadioButtonId) {
                    100 -> 0; 102 -> 2; else -> 1
                }
                pfeilRichtung = richtungen[gewaehlt[0]]
                pfeilDicke = when (gewaehlt[1]) {
                    0 -> 0.003f
                    2 -> 0.012f
                    else -> 0.006f
                }
                ansicht.setzePfeil(pfeilRichtung, pfeilDicke, pfeilFarbe)
                bericht.text = "Pfeil: " + anzeige[gewaehlt[0]] + ", " + dicken[gewaehlt[1]]
                bericht.text = "Pfeil: " + anzeige[gewaehlt[0]] + ", " + dicken[gewaehlt[1]]
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /** Stil und Größe für den Text — der Text selbst kommt nach dem Ziehen. */
    private fun textStilWaehlen() {
        val stile = arrayOf("normal", "fett", "kursiv", "fettkursiv")
        val anzeige = arrayOf("normal", "fett", "kursiv", "fett und kursiv")
        val stapel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        stapel.addView(TextView(this).apply { text = "Schriftstil" })
        val stilWahl = RadioGroup(this)
        for (i in anzeige.indices) {
            stilWahl.addView(android.widget.RadioButton(this).apply {
                id = 200 + i
                text = anzeige[i]
                isChecked = i == 0
            })
        }
        stapel.addView(stilWahl)
        val beschriftung = TextView(this).apply { text = "Größe: 3"; setPadding(0, 16, 0, 0) }
        stapel.addView(beschriftung)
        val regler = SeekBar(this).apply { max = 9; progress = 2 }
        regler.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, wert: Int, vonNutzer: Boolean) {
                beschriftung.text = "Größe: " + (wert + 1)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        stapel.addView(regler)
        farbReihe(stapel, "Farbe", textFarbe) { textFarbe = it }

        AlertDialog.Builder(this@MarkierActivity)
            .setTitle("Text")
            .setView(stapel)
            .setPositiveButton("OK") { _, _ ->
                val stil = stile[when (stilWahl.checkedRadioButtonId) {
                    201 -> 1; 202 -> 2; 203 -> 3; else -> 0
                }]
                val groesse = (regler.progress + 1) * 0.008f
                ansicht.setzeText(stil, groesse, textFarbe)
                bericht.text = "Text: $stil, Größe ${regler.progress + 1} — jetzt ziehen"
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /** Stempel: Text und Größe in einem Dialog (wie beim Text-Werkzeug). */
    private fun stempelWaehlen() {
        val liste = arrayOf("GEPRÜFT", "VERTRAULICH", "KOPIE", "ENTWURF",
                            "UNGÜLTIG", "BEZAHLT", "ERLEDIGT")
        val stapel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        stapel.addView(TextView(this).apply { text = "Stempeltext" })
        val wahl = RadioGroup(this)
        for (i in liste.indices) {
            wahl.addView(android.widget.RadioButton(this).apply {
                id = 300 + i
                text = liste[i]
                isChecked = i == 0
            })
        }
        stapel.addView(wahl)
        val beschriftung = TextView(this).apply { text = "Größe: 4"; setPadding(0, 16, 0, 0) }
        stapel.addView(beschriftung)
        val regler = SeekBar(this).apply { max = 9; progress = 3 }
        regler.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, wert: Int, vonNutzer: Boolean) {
                beschriftung.text = "Größe: " + (wert + 1)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        stapel.addView(regler)
        stapel.addView(TextView(this).apply { text = "Schriftstil"; setPadding(0, 16, 0, 0) })
        val stile = arrayOf("normal", "fett", "kursiv", "fettkursiv")
        val stilWahl = RadioGroup(this)
        for (i in stile.indices) {
            stilWahl.addView(android.widget.RadioButton(this).apply {
                id = 400 + i
                text = stile[i]
                isChecked = i == 0
            })
        }
        stapel.addView(stilWahl)
        farbReihe(stapel, "Rahmenfarbe", stempelRahmen) { stempelRahmen = it }
        farbReihe(stapel, "Schriftfarbe", stempelFarbe) { stempelFarbe = it }

        AlertDialog.Builder(this@MarkierActivity)
            .setTitle("Stempel")
            .setView(stapel)
            .setPositiveButton("OK") { _, _ ->
                val gewaehlt = wahl.checkedRadioButtonId - 300
                stempel = liste[if (gewaehlt in liste.indices) gewaehlt else 0]
                stempelGroesse = (regler.progress + 1) * 0.008f
                val stil = stile[when (stilWahl.checkedRadioButtonId) {
                    401 -> 1; 402 -> 2; 403 -> 3; else -> 0
                }]
                ansicht.setzeStempel(stempel, stempelGroesse, stil, stempelFarbe, stempelRahmen)
                bericht.text = "Stempel: $stempel, Größe ${regler.progress + 1}"
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun fertig() {
        val formen = ansicht.formen
        if (formen.isEmpty()) {
            setResult(RESULT_CANCELED); finish(); return
        }
        val reihe = org.json.JSONArray()
        for (f in formen) {
            reihe.put(org.json.JSONObject().apply {
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
            })
        }
        val daten = Intent()
        daten.putExtra("formen", reihe.toString())
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

    private var startX = 0f
    private var startY = 0f
    private var aktuell: RectF? = null
    private var werkzeug = "schwaerzen"
    private var stempelText = "GEPRÜFT"
    private var textStil = "normal"
    private var textGroesse = 0.024f
    private var pfeilRichtung = "rechts"
    private var pfeilDicke = 0.006f
    private var stempelGroesse = 0.032f
    private var textFarbe = android.graphics.Color.BLACK
    private var pfeilFarbe = android.graphics.Color.BLACK
    private var stempelFarbe = android.graphics.Color.RED
    private var stempelRahmen = android.graphics.Color.RED

    fun setzeWerkzeug(wert: String) { werkzeug = wert }
    private var stempelStil = "normal"

    fun setzeStempel(wert: String, groesse: Float, stil: String,
                     farbe: Int, rahmen: Int) {
        stempelText = wert
        stempelGroesse = groesse
        stempelStil = stil
        stempelFarbe = farbe
        stempelRahmen = rahmen
    }
    fun setzeText(stil: String, groesse: Float, farbe: Int) {
        textStil = stil; textGroesse = groesse; textFarbe = farbe
    }
    fun setzePfeil(richtung: String, dicke: Float, farbe: Int) {
        pfeilRichtung = richtung; pfeilDicke = dicke; pfeilFarbe = farbe
    }

    fun rueckgaengig() {
        if (formen.isNotEmpty()) { formen.removeAt(formen.size - 1); invalidate() }
    }

    private fun ziel(): RectF {
        val s = minOf(width.toFloat() / bild.width, height.toFloat() / bild.height)
        val b = RectF(0f, 0f, bild.width * s, bild.height * s)
        b.offset((width - b.width()) / 2f, (height - b.height()) / 2f)
        return b
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val z = ziel()
        canvas.drawBitmap(bild, null, z, null)
        for (f in formen) zeichne(canvas, f, z)
        aktuell?.let {
            canvas.drawRect(it, Paint().apply {
                color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 4f
            })
        }
    }

    private fun zeichne(canvas: Canvas, f: Form, z: RectF) {
        val links = z.left + f.x0 * z.width()
        val oben = z.top + f.y0 * z.height()
        val rechts = z.left + f.x1 * z.width()
        val unten = z.top + f.y1 * z.height()
        val dicke = (if (f.dicke > 0f) f.dicke else 0.006f) * z.height()
        when (f.typ) {
            "schwaerzen" -> canvas.drawRect(links, oben, rechts, unten,
                Paint().apply { color = Color.BLACK })
            "hervorheben" -> canvas.drawRect(links, oben, rechts, unten,
                Paint().apply { color = Color.argb(120, 255, 235, 0) })
            "unterstreichen" -> canvas.drawRect(links, unten - dicke * 1.5f, rechts, unten,
                Paint().apply { color = Color.BLACK })
            "durchstreichen" -> canvas.drawRect(links, (oben + unten) / 2f - dicke, rechts,
                (oben + unten) / 2f + dicke, Paint().apply { color = Color.BLACK })
            "pfeil" -> {
                canvas.drawLine(links, oben, rechts, unten, Paint().apply {
                    color = f.farbe; strokeWidth = dicke; style = Paint.Style.STROKE
                })
                val voll = Paint().apply { color = f.farbe }
                if (f.richtung == "rechts" || f.richtung == "beide") {
                    canvas.drawPath(Formen.spitze(links, oben, rechts, unten, dicke * 6f), voll)
                }
                if (f.richtung == "links" || f.richtung == "beide") {
                    canvas.drawPath(Formen.spitze(rechts, unten, links, oben, dicke * 6f), voll)
                }
            }
            "text" -> canvas.drawText(f.text.ifBlank { "Text" }, links, unten - 4f,
                Paint().apply {
                    color = f.farbe
                    textSize = (if (f.groesse > 0f) f.groesse else 0.03f) * z.height()
                    typeface = Formen.schrift(f.stil)
                })
            "stempel" -> {
                canvas.drawRect(links, oben, rechts, unten,
                    Paint().apply { color = Color.argb(210, 255, 255, 255) })
                canvas.drawRect(links, oben, rechts, unten, Paint().apply {
                    color = f.rahmenFarbe; style = Paint.Style.STROKE; strokeWidth = dicke
                })
                val stempelGroesse = if (f.groesse > 0f) f.groesse * z.height()
                                     else ((unten - oben) * 0.6f).coerceAtLeast(18f)
                canvas.drawText(f.text.ifBlank { "GEPRÜFT" }, links + 8f,
                    (oben + unten) / 2f + stempelGroesse / 3f,
                    Paint().apply {
                        color = f.farbe; textSize = stempelGroesse
                        typeface = Formen.schrift(f.stil)
                    })
            }
            "verwischen" -> {
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
                    if (werkzeug == "text") {
                        val feld = EditText(context)
                        AlertDialog.Builder(context)
                            .setTitle("Text in den Rahmen")
                            .setView(feld)
                            .setPositiveButton("OK") { _, _ ->
                                formen.add(Form("text", nx(r.left), ny(r.top),
                                                nx(r.right), ny(r.bottom),
                                                feld.text.toString().ifBlank { "Text" },
                                                textGroesse, textStil, 0f, "rechts",
                                                textFarbe, textFarbe))
                                invalidate()
                            }
                            .setNegativeButton("Abbrechen", null)
                            .show()
                    } else {
                        val text = when (werkzeug) {
                            "stempel" -> stempelText
                            else -> ""
                        }
                        val formGroesse = if (werkzeug == "stempel") stempelGroesse else 0f
                        val stil = if (werkzeug == "stempel") stempelStil else textStil
                        val farbe = if (werkzeug == "stempel") stempelFarbe else pfeilFarbe
                        val rahmen = if (werkzeug == "stempel") stempelRahmen else farbe
                        formen.add(Form(werkzeug, nx(r.left), ny(r.top),
                                        nx(r.right), ny(r.bottom), text,
                                        formGroesse, stil, pfeilDicke, pfeilRichtung,
                                        farbe, rahmen))
                        invalidate()
                    }
                }
            }
        }
        invalidate()
        return true
    }
}
