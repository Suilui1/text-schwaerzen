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
    // PDF-Betrieb (27.11.2026): Julius — „android lässt auch nicht im pdf blättern".
    // Jetzt bleibt der Renderer offen, die Seiten werden auf Abruf gerendert, und jede
    // Seite behält ihre eigenen Marken.
    private var pdfQuelle: android.net.Uri? = null
    private var pdfName: String = "dokument"
    private var renderer: android.graphics.pdf.PdfRenderer? = null
    private var descriptor: android.os.ParcelFileDescriptor? = null
    private var seite = 0
    private var seitenZahl = 0
    private val formenJeSeite = mutableMapOf<Int, MutableList<Form>>()
    private var seitenText: TextView? = null
    private var seiteZurueck: Button? = null
    private var seiteVor: Button? = null
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
        "verwischen" to "Verwischen",
        "anfassen" to "Anfassen…"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val direkt = uebergebenesBild
        uebergebenesBild = null
        val pdfText = intent.getStringExtra("pdf")
        val datei = intent.getStringExtra("datei")
        val geladen: Bitmap? = if (pdfText != null) {
            // PDF: Renderer öffnen und die erste Seite zeichnen.
            pdfQuelle = android.net.Uri.parse(pdfText)
            pdfName = intent.getStringExtra("name") ?: "dokument"
            try {
                val zwischen = java.io.File(cacheDir, "editor-quelle.pdf")
                contentResolver.openInputStream(pdfQuelle!!)?.use { quelle ->
                    zwischen.outputStream().use { ziel -> quelle.copyTo(ziel) }
                }
                descriptor = android.os.ParcelFileDescriptor.open(
                    zwischen, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = android.graphics.pdf.PdfRenderer(descriptor!!)
                seitenZahl = renderer?.pageCount ?: 0
                if (seitenZahl > 0) seiteBild(0) else null
            } catch (fehler: Throwable) {
                null
            }
        } else {
            direkt ?: try {
                if (datei == null) null else BitmapFactory.decodeFile(datei)
            } catch (fehler: Throwable) {
                null
            }
        }
        if (geladen == null) {
            setResult(RESULT_CANCELED); finish(); return
        }
        bild = geladen
        ansicht = MarkierAnsicht(this, bild)
        if (pdfQuelle != null) formenJeSeite[0] = ansicht.formen

        val rahmen = FrameLayout(this)
        rahmen.addView(ansicht, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val leiste = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((wert, anzeige) in werkzeuge) {
            leiste.addView(Button(this).apply {
                text = anzeige
                // Auf schmalen Geräten wurden „TEXT…" und „RÜCKGÄNGIG" abgeschnitten
                // (Bildschirmfoto Julius, 25.11.2026). Eine Zeile, kleinere Schrift,
                // die Leiste rollt ohnehin.
                isSingleLine = true
                textSize = 13f
                setPadding(24, 18, 24, 18)
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

        // Seitenleiste nur bei PDF — bei einem Bild gibt es nichts zu blättern.
        if (pdfQuelle != null) {
            val leisteSeiten = seitenLeiste()
            val seitenRahmen = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL)
            seitenRahmen.bottomMargin = 90
            rahmen.addView(leisteSeiten, seitenRahmen)
            // Bericht nach unten, damit er die Seitenleiste nicht überdeckt.
            berichtRahmen.topMargin = 130
        }

        val unten = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        unten.addView(Button(this).apply {
            text = "Rückgängig"
            isSingleLine = true
            textSize = 14f
            setOnClickListener { ansicht.rueckgaengig() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        unten.addView(Button(this).apply {
            text = "Abbrechen"
            isSingleLine = true
            textSize = 14f
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        unten.addView(Button(this).apply {
            text = "Fertig"
            isSingleLine = true
            textSize = 14f
            setOnClickListener { fertig() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        rahmen.addView(unten, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM))

        setContentView(rahmen)
        Darstellung.anwenden(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { renderer?.close() } catch (fehler: Throwable) { }
        try { descriptor?.close() } catch (fehler: Throwable) { }
        renderer = null
        descriptor = null
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
        "anfassen" -> " — Pfeil oder Form antippen, dann am Ende ziehen: drehen und verlängern."
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
        val regler = SeekBar(this).apply { max = 14; progress = 2 }
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
                // bis 15 % der Bildhöhe - vorher war bei 8 % Schluß (25.11.2026)
                val groesse = (regler.progress + 1) * 0.01f
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
        val regler = SeekBar(this).apply { max = 14; progress = 3 }
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
                stempelGroesse = (regler.progress + 1) * 0.01f
                val stil = stile[when (stilWahl.checkedRadioButtonId) {
                    401 -> 1; 402 -> 2; 403 -> 3; else -> 0
                }]
                ansicht.setzeStempel(stempel, stempelGroesse, stil, stempelFarbe, stempelRahmen)
                bericht.text = "Stempel: $stempel, Größe ${regler.progress + 1}"
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /** Rendert eine Seite in ein Bild (Breite 1400 — genug zum Arbeiten, wenig Speicher). */
    private fun seiteBild(nummer: Int): Bitmap? {
        val r = renderer ?: return null
        if (nummer < 0 || nummer >= r.pageCount) return null
        val p = r.openPage(nummer)
        val breite = 1400
        val hoehe = (breite.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
        val bild = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bild).drawColor(android.graphics.Color.WHITE)
        p.render(bild, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        p.close()
        return bild
    }

    /** Zeigt Seite `nummer`; die Marken der bisherigen Seite bleiben erhalten. */
    private fun zeigeSeite(nummer: Int) {
        if (renderer == null) return
        val neu = nummer.coerceIn(0, maxOf(0, seitenZahl - 1))
        formenJeSeite[seite] = ansicht.formen
        seite = neu
        val bild = seiteBild(seite) ?: return
        val marken = formenJeSeite.getOrPut(seite) { mutableListOf() }
        ansicht.setzeSeite(bild, marken)
        seitenText?.text = "Seite " + (seite + 1) + " von " + seitenZahl
        // An den Enden ist ein Pfeil sinnlos — ausgegraut statt ins Leere klickbar.
        seiteZurueck?.isEnabled = seite > 0
        seiteVor?.isEnabled = seite < seitenZahl - 1
    }

    private fun seitenLeiste(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(24, 8, 24, 8)
        seiteZurueck = Button(this@MarkierActivity).apply {
            text = "◀"
            isSingleLine = true
            setOnClickListener { zeigeSeite(seite - 1) }
        }
        seiteVor = Button(this@MarkierActivity).apply {
            text = "▶"
            isSingleLine = true
            setOnClickListener { zeigeSeite(seite + 1) }
        }
        seitenText = TextView(this@MarkierActivity).apply {
            text = "Seite 1 von " + seitenZahl
            textSize = 14f
            setPadding(24, 0, 24, 0)
        }
        addView(seiteZurueck)
        addView(seitenText)
        addView(seiteVor)
        seiteZurueck?.isEnabled = false
        seiteVor?.isEnabled = seitenZahl > 1
    }

    private fun fertig() {
        val formen = ansicht.formen
        if (pdfQuelle != null) {
            // PDF: alle Seiten mit ihren Marken zurückgeben.
            formenJeSeite[seite] = formen
            val gesamt = org.json.JSONObject()
            var anzahl = 0
            for ((nummer, marken) in formenJeSeite) {
                if (marken.isEmpty()) continue
                val reihe = org.json.JSONArray()
                for (f in marken) reihe.put(Formen.alsJson(f))
                gesamt.put(nummer.toString(), reihe)
                anzahl += marken.size
            }
            if (anzahl == 0) {
                setResult(RESULT_CANCELED); finish(); return
            }
            val daten = Intent()
            daten.putExtra("seiten", gesamt.toString())
            daten.putExtra("pdf", pdfQuelle.toString())
            daten.putExtra("name", pdfName)
            setResult(RESULT_OK, daten)
            finish()
            return
        }
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
class MarkierAnsicht(kontext: android.content.Context, bild: Bitmap) : View(kontext) {

    /** Das Bild der aktuellen Seite — beim Blättern ausgetauscht (27.11.2026). */
    var bild: Bitmap = bild
        set(wert) {
            field = wert
            ausgewaehlt = null
            invalidate()
        }

    /** Die Marken der aktuellen Seite — beim Blättern ausgetauscht. */
    var formen: MutableList<Form> = mutableListOf()

    /** Alles verwerfen, wenn die Seite wechselt. */
    fun setzeSeite(neuesBild: Bitmap, neueFormen: MutableList<Form>) {
        bild = neuesBild
        formen = neueFormen
        invalidate()
    }

    private var startX = 0f
    private var startY = 0f
    private var aktuell: RectF? = null
    private var ausgewaehlt: Int? = null
    private var griffAktiv = false
    private var griffAnfang = false
    private var verschieben = false
    private var versatzX = 0f
    private var versatzY = 0f

    /** Abstand eines Punktes zur Strecke (a,b) — für schmale Pfeile. */
    private fun abstandZurStrecke(px: Float, py: Float, ax: Float, ay: Float,
                                  bx: Float, by: Float): Double {
        val dx = (bx - ax).toDouble()
        val dy = (by - ay).toDouble()
        val laenge = dx * dx + dy * dy
        if (laenge < 0.0001) return Math.hypot((px - ax).toDouble(), (py - ay).toDouble())
        var t = ((px - ax) * dx + (py - ay) * dy) / laenge
        t = t.coerceIn(0.0, 1.0)
        return Math.hypot((px - (ax + t * dx)).toDouble(), (py - (ay + t * dy)).toDouble())
    }
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
        // Absichtlich wehrhaft: „Rückgängig" hat das Fenster geschlossen
        // (Julius, 25.11.2026). Wenn hier etwas wirft, darf die App nicht sterben —
        // die Meldung erscheint, und der Stand bleibt.
        try {
            if (formen.isEmpty()) return
            formen.removeAt(formen.size - 1)
            if (ausgewaehlt != null && ausgewaehlt!! >= formen.size) ausgewaehlt = null
            griffAktiv = false
            verschieben = false
            invalidate()
        } catch (fehler: Throwable) {
            android.widget.Toast.makeText(context,
                "Rückgängig nicht möglich: " + fehler.javaClass.simpleName,
                android.widget.Toast.LENGTH_LONG).show()
        }
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
        ausgewaehlt?.let { i ->
            val f = formen.getOrNull(i) ?: return@let
            val x0 = z.left + f.x0 * z.width()
            val y0 = z.top + f.y0 * z.height()
            val x1 = z.left + f.x1 * z.width()
            val y1 = z.top + f.y1 * z.height()
            canvas.drawCircle(x1, y1, 16f, Paint().apply {
                color = Color.argb(210, 0, 160, 255); style = Paint.Style.FILL
            })
            canvas.drawCircle(x0, y0, 16f, Paint().apply {
                color = Color.argb(210, 255, 150, 0); style = Paint.Style.FILL
            })
            canvas.drawLine(x0, y0, x1, y1, Paint().apply {
                color = Color.argb(160, 0, 160, 255); strokeWidth = 3f
                style = Paint.Style.STROKE
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
                val sx = z.left + f.x0 * z.width()
                val sy = z.top + f.y0 * z.height()
                val ex = z.left + f.x1 * z.width()
                val ey = z.top + f.y1 * z.height()
                canvas.drawLine(sx, sy, ex, ey, Paint().apply {
                    color = f.farbe; strokeWidth = dicke; style = Paint.Style.STROKE
                })
                val voll = Paint().apply { color = f.farbe }
                if (f.richtung == "rechts" || f.richtung == "beide") {
                    canvas.drawPath(Formen.spitze(sx, sy, ex, ey, dicke * 6f), voll)
                }
                if (f.richtung == "links" || f.richtung == "beide") {
                    canvas.drawPath(Formen.spitze(ex, ey, sx, sy, dicke * 6f), voll)
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
                // Vorschau: den Ausschnitt klein und wieder groß zeichnen — weich, so wie das
                // Ergebnis. Vorher lagen graue Kästchen darüber; das sah nach Raster aus
                // (Julius, 26.11.2026: „verwischen nicht so schön").
                val kasten = RectF(links, oben, rechts, unten)
                val faktor = 10f
                val klein = Bitmap.createBitmap(
                    ((rechts - links) / faktor).toInt().coerceAtLeast(1),
                    ((unten - oben) / faktor).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888)
                val maler = Canvas(klein)
                maler.drawBitmap(bild,
                    android.graphics.Rect(links.toInt(), oben.toInt(),
                                          rechts.toInt(), unten.toInt()),
                    RectF(0f, 0f, klein.width.toFloat(), klein.height.toFloat()),
                    Paint().apply { isFilterBitmap = true })
                canvas.drawBitmap(klein, null, kasten,
                    Paint().apply { isFilterBitmap = true })
                klein.recycle()
            }
        }
    }

    /** Auswählen und am Endpunkt ziehen: dreht und verlängert die Form. */
    private fun anfassen(ereignis: MotionEvent): Boolean {
        val z = ziel()
        fun nx(x: Float) = ((x - z.left) / z.width()).coerceIn(0f, 1f)
        fun ny(y: Float) = ((y - z.top) / z.height()).coerceIn(0f, 1f)
        when (ereignis.action) {
            MotionEvent.ACTION_DOWN -> {
                // nächste Form suchen, deren Bereich den Finger enthält
                var gefunden: Int? = null
                for (i in formen.indices.reversed()) {
                    val f = formen[i]
                    val x0 = z.left + f.x0 * z.width()
                    val x1 = z.left + f.x1 * z.width()
                    val y0 = z.top + f.y0 * z.height()
                    val y1 = z.top + f.y1 * z.height()
                    val links = minOf(x0, x1) - 24f
                    val rechts = maxOf(x0, x1) + 24f
                    val oben = minOf(y0, y1) - 24f
                    val unten = maxOf(y0, y1) + 24f
                    if (ereignis.x in links..rechts && ereignis.y in oben..unten) {
                        gefunden = i
                        break
                    }
                }
                ausgewaehlt = gefunden
            }
            MotionEvent.ACTION_MOVE -> {
                val i = ausgewaehlt ?: return true
                val f = formen.getOrNull(i) ?: return true
                f.x1 = nx(ereignis.x)
                f.y1 = ny(ereignis.y)
            }
        }
        invalidate()
        return true
    }

    override fun onTouchEvent(ereignis: MotionEvent): Boolean {
        if (werkzeug == "anfassen") return anfassen(ereignis)
        // Griff am Endpunkt: funktioniert in jedem Werkzeug, damit Drehen nicht erst
        // gesucht werden muß (Julius, 25.11.2026: „nichts ist drehbar").
        if (ereignis.action == MotionEvent.ACTION_DOWN) {
            val z = ziel()
            for (i in formen.indices.reversed()) {
                val f = formen[i]
                val ex = z.left + f.x1 * z.width()
                val ey = z.top + f.y1 * z.height()
                val sx = z.left + f.x0 * z.width()
                val sy = z.top + f.y0 * z.height()
                val dEnde = Math.hypot((ereignis.x - ex).toDouble(),
                                       (ereignis.y - ey).toDouble())
                val dAnfang = Math.hypot((ereignis.x - sx).toDouble(),
                                         (ereignis.y - sy).toDouble())

                // Zuerst prüfen: liegt der Finger INNEN? Dann verschieben.
                // Vorher hatten die Griffe 44 Pixel und lagen bei kleinen Formen
                // (Text, Stempel) über der ganzen Fläche — dadurch wurde nur
                // gestreckt statt verschoben (Julius, 25.11.2026).
                val innen = if (f.typ == "pfeil") {
                    abstandZurStrecke(ereignis.x, ereignis.y, sx, sy, ex, ey) < 26.0
                } else {
                    ereignis.x in minOf(sx, ex)..maxOf(sx, ex) &&
                        ereignis.y in minOf(sy, ey)..maxOf(sy, ey)
                }
                val griffFrei = minOf(dEnde, dAnfang) > 34.0

                if (innen && griffFrei) {
                    ausgewaehlt = i
                    verschieben = true
                    versatzX = ereignis.x - (sx + ex) / 2f
                    versatzY = ereignis.y - (sy + ey) / 2f
                    invalidate()
                    return true
                }
                if (dEnde < 44.0 || dAnfang < 44.0) {
                    ausgewaehlt = i
                    griffAktiv = true
                    griffAnfang = dAnfang < dEnde
                    invalidate()
                    return true
                }
            }
        }
        if (verschieben) {
            val z = ziel()
            val f = ausgewaehlt?.let { formen.getOrNull(it) } ?: return true
            if (ereignis.action == MotionEvent.ACTION_MOVE) {
                val mitteX = ((ereignis.x - versatzX - z.left) / z.width()).coerceIn(0f, 1f)
                val mitteY = ((ereignis.y - versatzY - z.top) / z.height()).coerceIn(0f, 1f)
                val halbeB = (f.x1 - f.x0) / 2f
                val halbeH = (f.y1 - f.y0) / 2f
                f.x0 = mitteX - halbeB; f.x1 = mitteX + halbeB
                f.y0 = mitteY - halbeH; f.y1 = mitteY + halbeH
                invalidate()
            }
            if (ereignis.action == MotionEvent.ACTION_UP) {
                verschieben = false
                invalidate()
            }
            return true
        }
        if (griffAktiv) {
            val z = ziel()
            val f = ausgewaehlt?.let { formen.getOrNull(it) } ?: return true
            if (ereignis.action == MotionEvent.ACTION_MOVE) {
                val neuX = ((ereignis.x - z.left) / z.width()).coerceIn(0f, 1f)
                val neuY = ((ereignis.y - z.top) / z.height()).coerceIn(0f, 1f)
                if (griffAnfang) {
                    f.x0 = neuX; f.y0 = neuY
                } else {
                    f.x1 = neuX; f.y1 = neuY
                }
                invalidate()
            }
            if (ereignis.action == MotionEvent.ACTION_UP) {
                griffAktiv = false
                invalidate()
            }
            return true
        }
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
                        // Pfeile behalten Start und Ende; alle anderen werden sortiert.
                        val istPfeil = werkzeug == "pfeil"
                        val formGroesse = if (werkzeug == "stempel") stempelGroesse else 0f
                        val stil = if (werkzeug == "stempel") stempelStil else textStil
                        val farbe = if (werkzeug == "stempel") stempelFarbe else pfeilFarbe
                        val rahmen = if (werkzeug == "stempel") stempelRahmen else farbe
                        if (istPfeil) {
                            formen.add(Form(werkzeug, nx(startX), ny(startY),
                                            nx(ereignis.x), ny(ereignis.y), "",
                                            formGroesse, stil, pfeilDicke, pfeilRichtung,
                                            farbe, rahmen))
                        } else {
                            formen.add(Form(werkzeug, nx(r.left), ny(r.top),
                                            nx(r.right), ny(r.bottom), text,
                                            formGroesse, stil, pfeilDicke, pfeilRichtung,
                                            farbe, rahmen))
                        }
                        invalidate()
                    }
                }
            }
        }
        invalidate()
        return true
    }
}
