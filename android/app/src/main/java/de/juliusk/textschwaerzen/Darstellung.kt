package de.juliusk.textschwaerzen

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.TextView

/**
 * Aussehen der App: Hell, Dunkel oder wie das Gerät (Julius' Wunsch, 25.11.2026:
 * „kann man die gesamte GUI verschönern mit Stil und Farbauswahl und hell dunkel modus").
 *
 * Bewußt ohne zusätzliche Bibliothek: runde Flächen, Akzentfarbe und Abstände werden
 * direkt gesetzt. Das hält das Paket klein und die App schnell.
 */
object Darstellung {

    enum class Modus { SYSTEM, HELL, DUNKEL }

    /** Formensprache der Oberflaeche - bewusst nur drei, sonst wird es Spielerei. */
    enum class Stil(val anzeige: String) {
        KLASSISCH("Klassisch (ruhig)"),
        WEICH("Weich (stark gerundet)"),
        ORNAMENT("Ornament (Rahmen mit Eckzier)")
    }

    // Farben
    private val AKZENT = Color.rgb(0, 122, 255)      // Blau wie in der Statusleiste
    private val AKZENT_DUNKEL = Color.rgb(10, 132, 255)
    private val HELL_HINTERGRUND = Color.rgb(248, 248, 250)
    private val HELL_FLAECHE = Color.WHITE
    private val HELL_TEXT = Color.rgb(28, 28, 30)
    private val DUNKEL_HINTERGRUND = Color.rgb(18, 18, 20)
    private val DUNKEL_FLAECHE = Color.rgb(38, 38, 42)
    private val DUNKEL_TEXT = Color.rgb(242, 242, 247)

    data class Farben(
        val hintergrund: Int, val flaeche: Int, val text: Int, val akzent: Int, val dunkel: Boolean
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences("darstellung", Context.MODE_PRIVATE)

    fun stil(context: Context): Stil {
        val wert = prefs(context).getString("stil", Stil.KLASSISCH.name) ?: Stil.KLASSISCH.name
        return try { Stil.valueOf(wert) } catch (fehler: Throwable) { Stil.KLASSISCH }
    }

    fun setzeStil(context: Context, stil: Stil) {
        prefs(context).edit().putString("stil", stil.name).apply()
    }

    fun modus(context: Context): Modus {
        val wert = prefs(context).getString("modus", Modus.SYSTEM.name) ?: Modus.SYSTEM.name
        return try {
            Modus.valueOf(wert)
        } catch (fehler: Throwable) {
            Modus.SYSTEM
        }
    }

    fun setzeModus(context: Context, modus: Modus) {
        prefs(context).edit().putString("modus", modus.name).apply()
    }

    /** Ist die App gerade dunkel? */
    fun istDunkel(context: Context): Boolean = when (modus(context)) {
        Modus.DUNKEL -> true
        Modus.HELL -> false
        Modus.SYSTEM -> {
            val nacht = (context.resources.configuration.uiMode
                         and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            nacht
        }
    }

    fun farben(context: Context): Farben {
        val dunkel = istDunkel(context)
        return if (dunkel) Farben(DUNKEL_HINTERGRUND, DUNKEL_FLAECHE, DUNKEL_TEXT,
                                  AKZENT_DUNKEL, true)
        else Farben(HELL_HINTERGRUND, HELL_FLAECHE, HELL_TEXT, AKZENT, false)
    }

    /** Wendet Hintergrund und Textfarbe auf eine Activity an. */
    fun anwenden(activity: Activity) {
        val f = farben(activity)
        activity.window?.decorView?.setBackgroundColor(f.hintergrund)
        if (stil(activity) == Stil.ORNAMENT) {
            try {
                val wurzel = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                if (wurzel != null && wurzel.findViewWithTag<android.view.View>("ornament") == null) {
                    val zier = OrnamentAnsicht(activity, f.akzent)
                    zier.tag = "ornament"
                    zier.isClickable = false
                    wurzel.addView(zier, android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                }
            } catch (fehler: Throwable) {
            }
        }
    }

    /** Macht aus einem gewöhnlichen Knopf einen runden, farbigen. */
    fun knopf(knopf: Button, context: Context, gefuellt: Boolean = false) {
        val f = farben(context)
        val grund = GradientDrawable()
        grund.cornerRadius = when (stil(context)) {
            Stil.WEICH -> 70f
            Stil.ORNAMENT -> 26f
            else -> 44f
        }
        grund.setColor(if (gefuellt) f.akzent else f.flaeche)
        grund.setStroke(2, if (f.dunkel) Color.rgb(70, 70, 76) else Color.rgb(209, 209, 214))
        knopf.background = grund
        knopf.setTextColor(if (gefuellt) Color.WHITE else f.akzent)
        knopf.textSize = 15f
        knopf.setAllCaps(false)
        knopf.minHeight = 0
        knopf.minimumHeight = 0
        knopf.setPadding(28, 22, 28, 22)
        knopf.elevation = if (f.dunkel) 0f else 2f
    }

    /** Titel und Fließtext passend einfärben. */
    fun titel(text: TextView, context: Context) {
        text.setTextColor(farben(context).text)
    }
}

/** Feiner Doppelrahmen mit Eckzier ueber die ganze Oberflaeche. */
class OrnamentAnsicht(kontext: android.content.Context, private val farbe: Int) :
    android.view.View(kontext) {

    private val aussen = android.graphics.Paint().apply {
        color = (farbe and 0x00FFFFFF) or (150 shl 24)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val innen = android.graphics.Paint().apply {
        color = (farbe and 0x00FFFFFF) or (80 shl 24)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }
    private val ecke = android.graphics.Paint().apply {
        color = (farbe and 0x00FFFFFF) or (150 shl 24)
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val rand = 12f
        val innenRand = 20f
        canvas.drawRect(rand, rand, width - rand, height - rand, aussen)
        canvas.drawRect(innenRand, innenRand, width - innenRand, height - innenRand, innen)
        val g = 16f
        for (paar in listOf(rand to rand, width - rand to rand,
                            rand to height - rand, width - rand to height - rand)) {
            val (x, y) = paar
            canvas.drawRect(x - g / 2, y - g / 2, x + g / 2, y + g / 2, ecke)
        }
    }
}
