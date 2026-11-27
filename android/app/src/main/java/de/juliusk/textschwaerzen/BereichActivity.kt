package de.juliusk.textschwaerzen

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

/**
 * „Bereich ziehen": ein Rahmen wird aufgezogen und dieser Bereich geschwärzt.
 *
 * Warum es das braucht (24.11.2026): Auf einer Fahrkarte steht ein QR-Code, der dieselben
 * Daten als Bild enthält. Die Regeln erkennen Text, keinen Code — dafür muß man den
 * Bereich mit dem Finger bestimmen können.
 *
 * Rückgabe: normalisierter Rahmen (0…1) in den Extras.
 */
class BereichActivity : Activity() {

    companion object {
        /** Das Bild wird direkt übergeben: Dekodieren in der Activity zeigte erst zwei
         *  leere Fenster, bevor das Bild erschien (Julius, 24.11.2026). */
        var uebergebenesBild: Bitmap? = null
    }

    private lateinit var bild: Bitmap
    private lateinit var ansicht: BereichsAnsicht

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Zuerst das übergebene Bild — dann ist das Fenster sofort gefüllt.
        val direkt = uebergebenesBild
        uebergebenesBild = null
        val datei = intent.getStringExtra("datei")
        val geladen = direkt ?: try {
            if (datei == null) {
                null
            } else {
                // Verkleinert laden: grosse Bilder machten das Ziehen traege (24.11.2026).
                val masse = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(datei, masse)
                var faktor = 1
                val groesste = maxOf(masse.outWidth, masse.outHeight)
                while (groesste / faktor > 1600) faktor *= 2
                val optionen = BitmapFactory.Options().apply { inSampleSize = faktor }
                BitmapFactory.decodeFile(datei, optionen)
            }
        } catch (fehler: Throwable) {
            null
        }
        if (geladen == null) {
            android.widget.Toast.makeText(this, Texte.t("bild_nicht_lesbar"), android.widget.Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        bild = geladen
        ansicht = BereichsAnsicht(this, bild)

        val rahmen = FrameLayout(this)
        rahmen.addView(ansicht, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val unten = TextView(this).apply {
            text = "Rahmen um den Bereich ziehen, der geschwärzt werden soll."
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            setPadding(24, 16, 24, 16)
        }
        rahmen.addView(unten, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP))
        setContentView(rahmen)

        val leiste = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }
        val abbrechen = Button(this).apply {
            text = Texte.t("abbrechen")
            setOnClickListener { setResult(RESULT_CANCELED); finish() }
        }
        val uebernehmen = Button(this).apply {
            text = Texte.t("schwaerzen")
            setOnClickListener {
                val r = ansicht.bereich()
                if (r == null) {
                    setResult(RESULT_CANCELED)
                } else {
                    val daten = Intent()
                    daten.putExtra("x0", r.left.toFloat() / bild.width)
                    daten.putExtra("y0", r.top.toFloat() / bild.height)
                    daten.putExtra("x1", r.right.toFloat() / bild.width)
                    daten.putExtra("y1", r.bottom.toFloat() / bild.height)
                    setResult(RESULT_OK, daten)
                }
                finish()
            }
        }
        leiste.addView(abbrechen, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        leiste.addView(uebernehmen, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        rahmen.addView(leiste, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM))
    }
}

/** Zeigt das Bild und läßt einen Rahmen aufziehen. */
class BereichsAnsicht(kontext: android.content.Context, private val bild: Bitmap) : View(kontext) {

    private var startX = 0f
    private var startY = 0f
    private var aktuell: Rect? = null
    private val stift = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val skala = minOf(width.toFloat() / bild.width, height.toFloat() / bild.height)
        val b = Rect(0, 0, (bild.width * skala).toInt(), (bild.height * skala).toInt())
        b.offset((width - b.width()) / 2, (height - b.height()) / 2)
        canvas.drawBitmap(bild, null, b, null)
        aktuell?.let { canvas.drawRect(it, stift) }
    }

    override fun onTouchEvent(ereignis: MotionEvent): Boolean {
        when (ereignis.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ereignis.x
                startY = ereignis.y
                aktuell = Rect(startX.toInt(), startY.toInt(), startX.toInt(), startY.toInt())
            }
            MotionEvent.ACTION_MOVE -> {
                aktuell = Rect(minOf(startX, ereignis.x).toInt(), minOf(startY, ereignis.y).toInt(),
                               maxOf(startX, ereignis.x).toInt(), maxOf(startY, ereignis.y).toInt())
            }
        }
        invalidate()
        return true
    }

    /** Rahmen in Bildpixeln (nicht in Fensterpixeln). */
    fun bereich(): Rect? {
        val r = aktuell ?: return null
        if (r.width() < 10 || r.height() < 10) return null
        val skala = minOf(width.toFloat() / bild.width, height.toFloat() / bild.height)
        val versatzX = (width - bild.width * skala) / 2
        val versatzY = (height - bild.height * skala) / 2
        return Rect(
            ((r.left - versatzX) / skala).toInt().coerceIn(0, bild.width),
            ((r.top - versatzY) / skala).toInt().coerceIn(0, bild.height),
            ((r.right - versatzX) / skala).toInt().coerceIn(0, bild.width),
            ((r.bottom - versatzY) / skala).toInt().coerceIn(0, bild.height)
        )
    }
}
