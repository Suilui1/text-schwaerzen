package de.juliusk.textschwaerzen

import android.graphics.Color

/** Farbpalette für Text, Pfeile und Stempel. */
object Farben {

    val NAMEN = listOf(
        "Schwarz", "Rot", "Blau", "Grün", "Gelb", "Orange", "Lila", "Weiß"
    )

    val WERTE = listOf(
        Color.BLACK, Color.RED, Color.BLUE, Color.GREEN,
        Color.YELLOW, Color.rgb(255, 140, 0), Color.rgb(128, 0, 128), Color.WHITE
    )

    fun name(farbe: Int): String {
        val i = WERTE.indexOf(farbe)
        return if (i >= 0) NAMEN[i] else "Farbe"
    }

    /** Gut lesbare Schrift auf hellem wie dunklem Grund. */
    fun lesbar(hintergrund: Int): Int {
        val helligkeit = (0.299 * Color.red(hintergrund) + 0.587 * Color.green(hintergrund)
                          + 0.114 * Color.blue(hintergrund))
        return if (helligkeit > 160) Color.BLACK else Color.WHITE
    }
}
