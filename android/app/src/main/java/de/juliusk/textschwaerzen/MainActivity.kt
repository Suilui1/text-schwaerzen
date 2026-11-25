package de.juliusk.textschwaerzen

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.app.AlertDialog
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Die Oberfläche: Bild wählen, Modus wählen, schwärzen, speichern.
 *
 * Absichtlich ohne Gradle-Compose: wenige Abhängigkeiten, kleineres Paket, und das
 * Programm soll auch auf alten Geräten laufen (minSdk 24).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var bildAnzeige: ImageView
    private lateinit var bericht: TextView
    private lateinit var bildWaehlen: Button
    private lateinit var speichern: Button

    private val threads = Executors.newSingleThreadExecutor()
    private val erkennung by lazy { Erkennung(applicationContext) }

    private var original: Bitmap? = null
    private var quelName: String = "bild"
    private var quelleName: String = "dokument"
    private var letzteDatei: android.net.Uri? = null
    private var letzterTyp: String = "image/png"
    private var fertig: Bitmap? = null
    private var modus = "gezielt"
    @Volatile private var abbrechenGewuenscht = false
    private val verlauf by lazy { getSharedPreferences("verlauf", MODE_PRIVATE) }

    /** Prüft, ob eine gemerkte Datei noch da ist (im Dateimanager gelöschte fallen raus). */
    private fun existiert(uri: android.net.Uri): Boolean {
        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (fehler: Throwable) {
            false
        }
    }

    /** Liest den Verlauf und wirft Einträge weg, deren Datei nicht mehr existiert. */
    private fun verlaufSaeubern(): List<String> {
        val roh = verlaufLesen()
        val gut = roh.filter { eintrag ->
            val uri = eintrag.split("|").getOrNull(1)?.let { android.net.Uri.parse(it) }
            uri != null && existiert(uri)
        }
        if (gut.size != roh.size) verlaufSchreiben(gut)
        return gut
    }

    /** Nimmt einen Eintrag aus der Liste, ohne die Datei zu löschen. */
    private fun ausListeEntfernen(eintrag: String) {
        val liste = verlaufLesen()
        liste.removeAll { it == eintrag }
        verlaufSchreiben(liste)
        Toast.makeText(this, "Aus der Liste entfernt", Toast.LENGTH_SHORT).show()
    }

    /** Merkt sich die zuletzt geöffneten Dateien (höchstens acht). */
    /**
     * Verlauf als geordnete Liste (JSON): neueste zuerst.
     *
     * Vorher lag er als Set vor — ein Set hat keine Reihenfolge, deshalb war die Liste
     * „Letzte Dateien" nicht chronologisch (Julius, 25.11.2026).
     */
    private fun verlaufLesen(): MutableList<String> {
        val roh = verlauf.getString("verlauf_json", "")
        if (roh.isNullOrBlank()) {
            // einmalige Übernahme des alten Sets, falls vorhanden
            val alt = (verlauf.getStringSet("dateien", emptySet()) ?: emptySet()).toList()
            if (alt.isNotEmpty()) {
                verlaufSchreiben(alt)
                verlauf.edit().remove("dateien").apply()
            }
            return alt.toMutableList()
        }
        return try {
            val reihe = org.json.JSONArray(roh)
            MutableList(reihe.length()) { reihe.getString(it) }
        } catch (fehler: Throwable) {
            mutableListOf()
        }
    }

    private fun verlaufSchreiben(eintraege: List<String>) {
        val reihe = org.json.JSONArray()
        eintraege.take(12).forEach { reihe.put(it) }
        verlauf.edit().putString("verlauf_json", reihe.toString()).apply()
    }

    private fun merken(uri: android.net.Uri, name: String, typ: String) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        val liste = verlaufLesen()
        liste.removeAll { it.split("|").getOrNull(1) == uri.toString() }
        // neueste vorn: Zeitstempel, Typ, URI, Name
        liste.add(0, typ + "|" + uri + "|" + name + "|" + System.currentTimeMillis())
        verlaufSchreiben(liste)
    }

    /** Zeigt die zuletzt geöffneten Bilder und Dokumente. */
    private fun letzteZeigen() {
        // Der Verlauf liegt geordnet vor (neueste zuerst) — alphabetisches Sortieren
        // machte die Liste unchronologisch (Julius, 25.11.2026).
        val eintraege = verlaufSaeubern()
        if (eintraege.isEmpty()) {
            Toast.makeText(this, "Noch keine Datei geöffnet", Toast.LENGTH_SHORT).show()
            return
        }
        // Nur der Dateiname: der Vorsatz („fertig", „PDF") sah unschön aus
        // (Julius, 25.11.2026). Die Art steckt ohnehin in der Endung.
        val namen = eintraege.map { teil ->
            teil.split("|").getOrElse(2) { "?" }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Letzte Dateien")
            .setItems(namen) { _, welcher ->
                val eintrag = eintraege[welcher]
                val stuecke = eintrag.split("|")
                val uri = android.net.Uri.parse(stuecke[1])
                val name = stuecke.getOrElse(2) { "Datei" }
                val istErgebnis = stuecke[0].endsWith("-aus")
                val auswahl = if (istErgebnis) {
                    arrayOf("Öffnen", "Ordner im Dateimanager zeigen", "Aus Liste entfernen", "Löschen")
                } else {
                    arrayOf("Öffnen", "Ordner im Dateimanager zeigen", "Aus Liste entfernen")
                }
                AlertDialog.Builder(this)
                    .setTitle(name)
                    .setItems(auswahl) { _, was ->
                        when {
                            was == 0 -> oeffneOderVerarbeite(stuecke[0], uri)
                            was == 1 -> ordnerZeigen(uri)
                            was == 2 -> ausListeEntfernen(eintrag)
                            was == 1 -> ausListeEntfernen(eintrag)
                            else -> loescheErgebnis(uri, name)
                        }
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
            .setNeutralButton("Liste leeren") { _, _ -> listeLeeren() }
            .setNegativeButton("Schliessen", null)
            .show()
    }

    /** Leert den ganzen Verlauf - die Dateien selbst bleiben erhalten. */
    private fun listeLeeren() {
        AlertDialog.Builder(this)
            .setTitle("Liste leeren?")
            .setMessage("Alle Eintraege werden aus der Liste entfernt.\n" +
                        "Die Dateien selbst bleiben auf dem Geraet.")
            .setPositiveButton("Leeren") { _, _ ->
                verlaufSchreiben(emptyList())
                Toast.makeText(this, "Liste geleert", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /** Öffnet Ergebnisse in einer anderen App, Quellen wieder im Programm. */
    private fun oeffneOderVerarbeite(art: String, uri: android.net.Uri) {
        when (art) {
            "pdf" -> pdfGewaehlt(uri)
            "text" -> textGewaehlt(uri)
            "bild" -> ladeBild(uri)
            "pdf-aus" -> oeffneExtern(uri, "application/pdf")
            "text-aus" -> oeffneExtern(uri, "text/plain")
            else -> oeffneExtern(uri, "image/png")
        }
    }

    private var fortschrittDialog: AlertDialog? = null
    private var fortschrittBalken: android.widget.ProgressBar? = null
    private var fortschrittText: android.widget.TextView? = null

    private val speichernBild = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { ziel -> if (ziel != null) kopiereLetzteNach(ziel) }

    private val speichernPdf = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { ziel -> if (ziel != null) kopiereLetzteNach(ziel) }

    private val speichernText = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { ziel -> if (ziel != null) kopiereLetzteNach(ziel) }

    private val bereichAuswahl = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { ergebnis ->
        if (ergebnis.resultCode != RESULT_OK) {
            bildWaehlen.isEnabled = true
            return@registerForActivityResult
        }
        val d = ergebnis.data ?: return@registerForActivityResult
        // Der Markier-Editor liefert eine Liste von Formen (Whiteout-Werkzeuge).
        val formen = Formen.vonJson(d.getStringExtra("formen"))
        if (formen.isNotEmpty()) {
            formenAnwenden(formen)
        } else {
            bereichSchwaerzen(d.getFloatExtra("x0", 0f), d.getFloatExtra("y0", 0f),
                              d.getFloatExtra("x1", 0f), d.getFloatExtra("y1", 0f))
        }
    }

    private val textAuswahl = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) dateiOeffnen(uri) }

    /** Ein Weg für alles: Bild, PDF und Text werden am Inhalt erkannt. */
    private fun dateiOeffnen(uri: android.net.Uri) {
        // nameDerDatei() schneidet die Endung ab — für die Erkennung braucht es den
        // vollen Namen, sonst wurde jede .txt als Bild geöffnet
        // („kann Bild nicht öffnen", Julius am 25.11.2026).
        val voll = vollerName(uri).lowercase()
        val endung = voll.substringAfterLast('.', "")
        val typ = try {
            (contentResolver.getType(uri) ?: "").lowercase()
        } catch (fehler: Exception) {
            ""
        }
        when {
            istPdf(uri) -> pdfGewaehlt(uri)
            typ.startsWith("text/") || endung in setOf("txt", "csv", "log", "md") ->
                textGewaehlt(uri)
            else -> ladeBild(uri)
        }
    }

    /** Anzeigename MIT Endung. */
    private fun vollerName(uri: android.net.Uri): String {
        var name = "datei"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { zeiger ->
                val spalte = zeiger.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (spalte >= 0 && zeiger.moveToFirst()) {
                    name = zeiger.getString(spalte) ?: name
                }
            }
        } catch (fehler: Exception) {
        }
        return name
    }

    private val pdfAuswahl = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pdfGewaehlt(uri) }

    /** Fragt nach der Stufe und schwärzt eine Textdatei. */
    private fun textGewaehlt(uri: android.net.Uri) {
        val name = nameDerDatei(uri)
        merken(uri, name, "text")
        AlertDialog.Builder(this)
            .setTitle("Text schwärzen — wie stark?")
            .setItems(arrayOf("Nur Adressen und Namen", "Alles", "Zeilen auswählen")) { _, welcher ->
                if (welcher == 2) {
                    zeilenAuswaehlen(uri, name)
                    return@setItems
                }
                val modusName = if (welcher == 0) "gezielt" else "alles"
                threads.execute {
                    val ergebnis = TextVerarbeitung.verarbeite(this, uri, modusName, name)
                    runOnUiThread {
                        if (ergebnis.fehler.isNotEmpty()) {
                            AlertDialog.Builder(this).setTitle("Fehler")
                                .setMessage(ergebnis.fehler)
                                .setPositiveButton("OK", null).show()
                            return@runOnUiThread
                        }
                        letzteDatei = ergebnis.datei
                        letzterTyp = "text/plain"
                        merken(ergebnis.datei ?: uri, name + "-geschwaerzt", "text-aus")
                        val teile = if (ergebnis.zaehler.isEmpty()) "nichts Sensibles gefunden"
                        else ergebnis.zaehler.entries.joinToString(", ") {
                            Regeln.bezeichnung(it.key) + " " + it.value
                        }
                        val text = ergebnis.zeilen.toString() + " Zeilen, " +
                            ergebnis.geschwaerzt + " geschwaerzt\n" + teile
                        bericht.text = text
                        abschlussDialog("Text geschwaerzt", text)
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /** Zeilen ankreuzen — das Gegenstück zum Rahmen bei Bild und PDF. */
    private fun zeilenAuswaehlen(uri: android.net.Uri, name: String) {
        val alle = TextVerarbeitung.zeilen(this, uri)
        if (alle.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Fehler")
                .setMessage("Datei nicht lesbar").setPositiveButton("OK", null).show()
            return
        }
        val angezeigt = alle.mapIndexed { nummer, zeile ->
            (nummer + 1).toString() + ":  " + zeile.take(70)
        }.toTypedArray()
        val gewaehlt = mutableSetOf<Int>()
        AlertDialog.Builder(this)
            .setTitle("Zeilen zum Schwärzen ankreuzen")
            .setMultiChoiceItems(angezeigt, null) { _, welche, istAn -> 
                if (istAn) gewaehlt.add(welche) else gewaehlt.remove(welche)
            }
            .setPositiveButton("Schwärzen") { _, _ ->
                threads.execute {
                    val ergebnis = TextVerarbeitung.verarbeiteAuswahl(this, uri, gewaehlt, name)
                    runOnUiThread {
                        if (ergebnis.fehler.isNotEmpty()) {
                            AlertDialog.Builder(this).setTitle("Fehler")
                                .setMessage(ergebnis.fehler).setPositiveButton("OK", null).show()
                            return@runOnUiThread
                        }
                        letzteDatei = ergebnis.datei
                        letzterTyp = "text/plain"
                        merken(ergebnis.datei ?: uri, name + "-geschwaerzt", "text-aus")
                        val text = ergebnis.geschwaerzt.toString() + " Zeile(n) geschwaerzt"
                        bericht.text = text
                        abschlussDialog("Text geschwaerzt", text)
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /** Fragt nach der Stufe und startet die Verarbeitung (auch aus dem Verlauf). */
    private fun pdfGewaehlt(uri: android.net.Uri) {
        // Nicht jede Quelle meldet ihre PDFs als "application/pdf" (manche als
        // application/octet-stream). Deshalb zeigen wir alle Dateien an und prüfen den
        // Inhalt selbst — am 24.11.2026 waren im Auswahlfenster keine PDFs zu sehen.
        if (!istPdf(uri)) {
            bericht.text = "Diese Datei ist kein PDF."
            return
        }
        merken(uri, nameDerDatei(uri), "pdf")
        AlertDialog.Builder(this)
            .setTitle("Wie stark schwärzen?")
            .setItems(arrayOf("Nur Adressen und Namen", "Alles", "Bereich ziehen")) { _, welcher ->
                if (welcher == 2) {
                    pdfRahmenZiehen(uri)
                    return@setItems
                }
                modus = if (welcher == 0) "gezielt" else "alles"
                startePdf(uri)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /** Prüft die Erkennung am mitgelieferten Testbild und meldet die Zahlen. */
    private fun selbsttestLaufen() {
        bericht.text = "Selbsttest laeuft …"
        threads.execute {
            val meldung = try {
                val strom = assets.open("vorlage/test.png")
                val bild = android.graphics.BitmapFactory.decodeStream(strom)
                strom.close()
                val woerter = erkennung.woerter(bild)
                val ergebnis = schwaerzen(bild, erkennung, "gezielt")
                "Testbild " + bild.width + "x" + bild.height +
                    "\nZustand: " + erkennung.zustand +
                    "\nWoerter erkannt: " + woerter.size +
                    "\nTreffer: " + ergebnis.zaehler.entries.joinToString(", ") {
                        Regeln.bezeichnung(it.key) + " " + it.value
                    }
            } catch (fehler: Throwable) {
                "Fehler im Selbsttest: " + fehler.javaClass.simpleName + ": " + fehler.message
            }
            runOnUiThread {
                bericht.text = meldung
                AlertDialog.Builder(this)
                    .setTitle("Selbsttest")
                    .setMessage(meldung)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun rahmenZiehen() {
        // Auf dem aktuellen Stand arbeiten: was die Regeln schon geschwaerzt haben, bleibt
        // geschwaerzt. Sonst muesste man die Datei zweimal oeffnen (Julius' Frage, 24.11.2026).
        val bild = fertig ?: original ?: return
        // Das Schreiben der PNG-Datei dauerte bei grossen Bildern mehrere Sekunden und
        // blockierte die Oberflaeche ("reagiert nicht", 24.11.2026) — deshalb im Hintergrund.
        bildWaehlen.isEnabled = false
        bericht.text = "Bereich-Editor wird vorbereitet …"
        threads.execute {
            try {
                // Verkleinern und direkt übergeben: kein Dekodieren in der Activity,
                // dadurch erscheint das Bild sofort (vorher zwei leere Fenster).
                // Höchstens 1200 Pixel: bei 12-Megapixel-Fotos reichte der Speicher sonst
                // nicht, und der Absturz war ein Error (kein Exception) — die App schloß
                // sich ohne Meldung (Julius, 25.11.2026).
                val groesste = maxOf(bild.width, bild.height)
                val anteil = if (groesste > 1200) 1200.0 / groesste else 1.0
                val breite = (bild.width * anteil).toInt().coerceAtLeast(1)
                val hoehe = (bild.height * anteil).toInt().coerceAtLeast(1)
                val klein = if (anteil >= 1.0) bild
                            else Bitmap.createScaledBitmap(bild, breite, hoehe, true)
                MarkierActivity.uebergebenesBild = klein
                runOnUiThread {
                    bildWaehlen.isEnabled = true
                    bericht.text = ""
                    val absicht = Intent(this, MarkierActivity::class.java)
                    bereichAuswahl.launch(absicht)
                }
            } catch (fehler: Exception) {
                runOnUiThread {
                    bildWaehlen.isEnabled = true
                    AlertDialog.Builder(this).setTitle("Fehler")
                        .setMessage(fehler.message).setPositiveButton("OK", null).show()
                }
            }
        }
    }

    /** Wendet die im Markier-Editor gezeichneten Formen auf das Bild an. */
    private fun formenAnwenden(formen: List<Form>) {
        val bild = fertig ?: original ?: return
        val kopie = bild.copy(Bitmap.Config.ARGB_8888, true)
        Formen.zeichne(kopie, formen)
        fertig = kopie
        bildAnzeige.setImageBitmap(kopie)
        bildWaehlen.isEnabled = true
        val arten = formen.map { it.typ }.distinct().size
        bericht.text = formen.size.toString() + " Form(en) gezeichnet.\n\n" +
                       "Vorschau antippen öffnet die Datei."
        speichereUndZeige(kopie, formen.size.toString() + " Form(en) gezeichnet")
    }

    private fun bereichSchwaerzen(x0: Float, y0: Float, x1: Float, y1: Float) {
        // Kam der Aufruf aus dem PDF-Weg, wird der Rahmen auf jede Seite angewendet.
        val pdf = pdfMitRahmen
        if (pdf != null) {
            pdfMitRahmen = null
            val rahmen = android.graphics.RectF(x0, y0, x1, y1)
            bericht.text = "PDF wird verarbeitet …"
            bildWaehlen.isEnabled = false
            threads.execute {
                runOnUiThread { fortschrittZeigen(0) }
                val ergebnis = PdfVerarbeitung.verarbeite(
                    this, pdf, erkennung, "nur_rahmen", nameDerDatei(pdf), rahmen,
                    stand = { seite, gesamt ->
                        runOnUiThread {
                            fortschrittBalken?.max = gesamt
                            fortschrittBalken?.progress = seite
                            fortschrittText?.text = "Seite $seite von $gesamt"
                        }
                    },
                    abgebrochen = { abbrechenGewuenscht }
                )
                runOnUiThread { fortschrittSchliessen() }
                if (abbrechenGewuenscht) {
                    runOnUiThread {
                        bildWaehlen.isEnabled = true
                        bericht.text = "Abgebrochen."
                        Toast.makeText(this, "Abgebrochen — nichts gespeichert",
                                       Toast.LENGTH_LONG).show()
                    }
                    return@execute
                }
                runOnUiThread {
                    bildWaehlen.isEnabled = true
                    letzteDatei = ergebnis.datei
                    letzterTyp = "application/pdf"
                    if (ergebnis.datei != null) {
                        merken(ergebnis.datei, nameDerDatei(pdf) + "-geschwaerzt", "pdf-aus")
                    }
                    val meldung = if (ergebnis.fehler.isNotEmpty()) "Fehler: " + ergebnis.fehler
                    else ergebnis.seiten.toString() + " Seite(n) mit Rahmen geschwaerzt"
                    bericht.text = meldung
                    abschlussDialog("PDF geschwaerzt", meldung)
                }
            }
            return
        }

        val bild = fertig ?: original ?: return
        val kopie = bild.copy(Bitmap.Config.ARGB_8888, true)
        val zeichner = Canvas(kopie)
        val stift = Paint().apply { color = Color.BLACK }
        zeichner.drawRect(x0 * kopie.width, y0 * kopie.height,
                          x1 * kopie.width, y1 * kopie.height, stift)
        fertig = kopie
        bildAnzeige.setImageBitmap(kopie)
        bildWaehlen.isEnabled = true
        // Auch hier sofort speichern und denselben Abschlussdialog zeigen.
        speichereUndZeige(kopie, "Bereich geschwaerzt")
    }

    private var pdfMitRahmen: android.net.Uri? = null

    private fun pdfRahmenZiehen(uri: android.net.Uri) {
        pdfMitRahmen = uri
        bericht.text = "Erste Seite wird vorbereitet …"
        threads.execute {
            val bild = PdfVerarbeitung.ersteSeiteAlsBild(this, uri)
            runOnUiThread {
                if (bild == null) {
                    bericht.text = "PDF nicht lesbar."
                    AlertDialog.Builder(this).setTitle("Fehler")
                        .setMessage("Die erste Seite konnte nicht gelesen werden.")
                        .setPositiveButton("OK", null).show()
                    return@runOnUiThread
                }
                bericht.text = ""
                val absicht = Intent(this, MarkierActivity::class.java)
                absicht.putExtra("datei", bild.absolutePath)
                bereichAuswahl.launch(absicht)
            }
        }
    }

    /** Fortschritt mit „Abbrechen" — bei 91 Seiten unverzichtbar. */
    private fun fortschrittZeigen(seiten: Int) {
        abbrechenGewuenscht = false
        val leiste = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        fortschrittText = android.widget.TextView(this).apply {
            text = "PDF wird verarbeitet …"
        }
        fortschrittBalken = android.widget.ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal).apply {
            max = seiten
            progress = 0
        }
        leiste.addView(fortschrittText)
        leiste.addView(fortschrittBalken)
        fortschrittDialog = AlertDialog.Builder(this)
            .setTitle("Schwärzen läuft")
            .setView(leiste)
            .setNegativeButton("Abbrechen") { _, _ -> abbrechenGewuenscht = true }
            .setCancelable(false)
            .show()
    }

    private fun fortschrittSchliessen() {
        fortschrittDialog?.dismiss()
        fortschrittDialog = null
        fortschrittBalken = null
        fortschrittText = null
    }

    private fun startePdf(uri: android.net.Uri) {
        val modusName = modus
        bericht.text = "PDF wird verarbeitet …"
        bildWaehlen.isEnabled = false
        bildWaehlen.isEnabled = false
        threads.execute {
            quelleName = nameDerDatei(uri)
            runOnUiThread { fortschrittZeigen(0) }
            val ergebnis = PdfVerarbeitung.verarbeite(
                this, uri, erkennung, modusName, quelleName, null,
                stand = { seite, gesamt ->
                    runOnUiThread {
                        fortschrittBalken?.max = gesamt
                        fortschrittBalken?.progress = seite
                        fortschrittText?.text = "Seite $seite von $gesamt"
                    }
                },
                abgebrochen = { abbrechenGewuenscht }
            )
            runOnUiThread { fortschrittSchliessen() }
            if (abbrechenGewuenscht) {
                runOnUiThread {
                    bildWaehlen.isEnabled = true
                    bildWaehlen.isEnabled = true
                    bericht.text = "Abgebrochen."
                    Toast.makeText(this, "Abgebrochen — nichts gespeichert", Toast.LENGTH_LONG).show()
                }
                return@execute
            }
            if (ergebnis.fehler.isNotEmpty()) {
                runOnUiThread {
                    bericht.text = "PDF fehlgeschlagen: ${ergebnis.fehler}"
                    AlertDialog.Builder(this)
                        .setTitle("PDF fehlgeschlagen")
                        .setMessage(ergebnis.fehler)
                        .setPositiveButton("OK", null)
                        .show()
                    bildWaehlen.isEnabled = true
                    bildWaehlen.isEnabled = true
                }
                return@execute
            }
            runOnUiThread {
                val teile = if (ergebnis.zaehler.isEmpty()) "nichts Sensibles gefunden"
                else ergebnis.zaehler.entries.joinToString(", ") {
                    "${Regeln.bezeichnung(it.key)} ${it.value}"
                }
                bericht.text = "${ergebnis.seiten} Seite(n): $teile\n${ergebnis.datei ?: "nicht gespeichert"}"
                letzteDatei = ergebnis.datei
                letzterTyp = "application/pdf"
                if (ergebnis.datei != null) {
                    merken(ergebnis.datei, quelleName + "-geschwaerzt", "pdf-aus")
                }
                bildWaehlen.isEnabled = true
                bildWaehlen.isEnabled = true
                abschlussDialog("PDF geschwaerzt", ergebnis.seiten.toString() + " Seite(n), " +
                                ergebnis.woerter + " Woerter erkannt\n" + teile)
            }
        }
    }

    private val auswahl = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) ladeBild(uri) }

    /** Lädt ein Bild — von der Auswahl oder aus der Liste der letzten Dateien. */
    private fun ladeBild(uri: android.net.Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { strom ->
                val bild = BitmapFactory.decodeStream(strom)
                if (bild == null) {
                    Toast.makeText(this, "Bild nicht lesbar", Toast.LENGTH_LONG).show()
                    return
                }
                original = bild
                letzteDatei = null   // neue Quelle: alte Vorschau gilt nicht mehr
                quelName = nameDerDatei(uri)
                merken(uri, quelName, "bild")
                fertig = null
                bildAnzeige.setImageBitmap(bild)
                bericht.text = "Bild geladen."
                speichern.isEnabled = false
                bildGewaehlt()
            }
        } catch (fehler: Exception) {
            Toast.makeText(this, "Bild nicht lesbar: " + fehler.message, Toast.LENGTH_LONG).show()
        }
    }

    /** Prüft die ersten Bytes: ein PDF beginnt mit "%PDF". */
    /** Anzeigename der gewählten Datei (ohne Endung) — daraus wird der Ergebnisname. */
    private fun nameDerDatei(uri: android.net.Uri): String {
        var name = "bild"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { zeiger ->
                val spalte = zeiger.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (spalte >= 0 && zeiger.moveToFirst()) {
                    name = zeiger.getString(spalte) ?: name
                }
            }
        } catch (_: Exception) {
        }
        return name.substringBeforeLast('.')
    }

    private fun istPdf(uri: android.net.Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { strom ->
                val kopf = ByteArray(4)
                val gelesen = strom.read(kopf)
                gelesen == 4 && String(kopf) == "%PDF"
            } ?: false
        } catch (fehler: Exception) {
            false
        }
    }

    /** Kopiert das zuletzt gespeicherte Ergebnis an einen frei gewählten Ort. */
    private fun kopiereLetzteNach(ziel: android.net.Uri) {
        val quelle = letzteDatei ?: return
        try {
            contentResolver.openInputStream(quelle)?.use { ein ->
                contentResolver.openOutputStream(ziel)?.use { aus -> ein.copyTo(aus) }
            }
            Toast.makeText(this, "Gespeichert: " + ziel, Toast.LENGTH_LONG).show()
        } catch (fehler: Exception) {
            Toast.makeText(this, "Speichern fehlgeschlagen: " + fehler.message,
                           Toast.LENGTH_LONG).show()
        }
    }

    /** Reicht das Ergebnis an eine andere App weiter (Mail, Messenger, Cloud). */
    private fun teileLetzte() {
        val uri = letzteDatei ?: return
        try {
            val absicht = Intent(Intent.ACTION_SEND).apply {
                type = letzterTyp
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(absicht, "Teilen"))
        } catch (fehler: Exception) {
            Toast.makeText(this, "Teilen nicht möglich", Toast.LENGTH_LONG).show()
        }
    }

    /** Dialog nach dem Speichern: mehr Möglichkeiten als nur "Öffnen". */
    private fun abschlussDialog(titel: String, text: String) {
        // „Öffnen" als eigener Knopf vorn — in der Liste war es nicht zu finden
        // (25.11.2026). Der Rest bleibt in der Liste.
        AlertDialog.Builder(this)
            .setTitle(titel)
            .setMessage(text)
            .setPositiveButton("Öffnen") { _, _ -> oeffneLetzte() }
            .setNeutralButton("Mehr…") { _, _ -> mehrDialog() }
            .setNegativeButton("Schließen", null)
            .show()
    }

    /** Weitere Möglichkeiten: speichern unter, teilen, löschen. */
    private fun mehrDialog() {
        AlertDialog.Builder(this)
            .setTitle("Mehr")
            .setItems(arrayOf("Speichern unter…", "Teilen", "Löschen")) { _, was ->
                when (was) {
                    0 -> {
                        if (letzterTyp == "application/pdf") {
                            speichernPdf.launch("geschwaerzt.pdf")
                        } else if (letzterTyp == "text/plain") {
                            speichernText.launch("geschwaerzt.txt")
                        } else {
                            speichernBild.launch("geschwaerzt.png")
                        }
                    }
                    1 -> teileLetzte()
                    else -> {
                        val uri = letzteDatei
                        if (uri == null) {
                            Toast.makeText(this, "Keine Datei zum Löschen", Toast.LENGTH_SHORT).show()
                        } else {
                            loescheErgebnis(uri, "Ergebnis")
                        }
                    }
                }
            }
            .setNegativeButton("Zurück", null)
            .show()
    }

    private fun abschlussDialogAlt(titel: String, text: String) {
        AlertDialog.Builder(this)
            .setTitle(titel)
            .setMessage(text)
            .setItems(arrayOf("Öffnen", "Speichern unter…", "Teilen", "Löschen")) { _, was ->
                when (was) {
                    0 -> oeffneLetzte()
                    1 -> {
                        if (letzterTyp == "application/pdf") {
                            speichernPdf.launch("geschwaerzt.pdf")
                        } else if (letzterTyp == "text/plain") {
                            speichernText.launch("geschwaerzt.txt")
                        } else {
                            speichernBild.launch("geschwaerzt.png")
                        }
                    }
                    2 -> teileLetzte()
                    else -> {
                        val uri = letzteDatei
                        if (uri == null) {
                            Toast.makeText(this, "Keine Datei zum Löschen", Toast.LENGTH_SHORT).show()
                        } else {
                            loescheErgebnis(uri, "Ergebnis")
                        }
                    }
                }
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    /** Zeigt alle geschwärzten Ergebnisse mit Öffnen und Löschen. */
    private fun ergebnisseZeigen() {
        val eintraege = verlaufSaeubern()
            .filter { it.startsWith("bild-aus|") || it.startsWith("pdf-aus|")
                      || it.startsWith("text-aus|") }
        if (eintraege.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Ergebnisse")
                .setMessage("Noch keine geschwärzte Datei gespeichert.\n\nNach dem Schwärzen " +
                            "liegt sie unter Bilder bzw. Downloads und erscheint hier.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val namen = eintraege.map { teil ->
            val stuecke = teil.split("|")
            (stuecke.getOrElse(2) { "Ergebnis" })
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Ergebnisse")
            .setItems(namen) { _, welcher ->
                val stuecke = eintraege[welcher].split("|")
                val uri = android.net.Uri.parse(stuecke[1])
                val name = stuecke.getOrElse(2) { "Ergebnis" }
                AlertDialog.Builder(this)
                    .setTitle(name)
                    .setItems(arrayOf("Öffnen", "Ordner im Dateimanager zeigen", "Aus Liste entfernen", "Löschen")) { _, was ->
                        if (was == 0) {
                            oeffneExtern(uri, when (stuecke[0]) {
                                "pdf-aus" -> "application/pdf"
                                "text-aus" -> "text/plain"
                                else -> "image/png"
                            })
                        } else if (was == 1) {
                            ordnerZeigen(uri)
                          } else if (was == 2) {
                              ausListeEntfernen(eintraege[welcher])
                        } else {
                            loescheErgebnis(uri, name)
                        }
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
            .setNeutralButton("Liste leeren") { _, _ -> listeLeeren() }
            .setNegativeButton("Schließen", null)
            .show()
    }

    /** Löscht ein geschwärtes Ergebnis nach Rückfrage. */
    private fun loescheErgebnis(uri: android.net.Uri, name: String) {
        AlertDialog.Builder(this)
            .setTitle("Löschen?")
            .setMessage(name + "\n\nDie Datei wird endgültig entfernt.")
            .setPositiveButton("Löschen") { _, _ ->
                try {
                    contentResolver.delete(uri, null, null)
                    val alt = verlauf.getStringSet("dateien", emptySet())?.toMutableSet()
                        ?: mutableSetOf<String>()
                    alt.removeAll { it.contains(uri.toString()) }
                    verlauf.edit().putStringSet("dateien", alt).apply()
                    Toast.makeText(this, "Gelöscht: " + name, Toast.LENGTH_SHORT).show()
                } catch (fehler: Throwable) {
                    Toast.makeText(this, "Löschen nicht möglich: " + fehler.message,
                                   Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /**
     * Öffnet den ORDNER der Datei im Dateimanager (Julius' Wahl „b", 25.11.2026).
     *
     * Android kennt keinen Befehl „zeige diese Datei". Ordner geht aber:
     *   · bei Dokumenten: den Elternordner als Dokument-URI
     *   · bei eigenen Ergebnissen: den Medien-Ordner (Pictures bzw. Download) über
     *     den Speicher-Anbieter „externalstorage"
     * Scheitert beides, öffnet die Dateiauswahl diesen Ordner als Startpunkt.
     */
    private fun ordnerZeigen(uri: android.net.Uri) {
        val ordner = ordnerUri(uri)
        if (ordner != null) {
            try {
                val absicht = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(ordner,
                        android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(absicht)
                return
            } catch (fehler: Throwable) {
                // kein Betrachter für Ordner — weiter mit der Auswahl
            }
            try {
                val auswahl = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, ordner)
                }
                startActivity(Intent.createChooser(auswahl, "Ordner zeigen"))
                return
            } catch (fehler: Throwable) {
                // weiter zur Meldung
            }
        }
        // 3. Rückfall: Dateiauswahl mit der DATEI als Startpunkt. Die Auswahl öffnet
        //    dann den Ordner, in dem sie liegt — auch wenn sich kein Ordner bauen ließ.
        try {
            val auswahl = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
            }
            startActivity(Intent.createChooser(auswahl, "Ordner zeigen"))
            return
        } catch (fehler: Throwable) {
        }
        Toast.makeText(this, "Ordner nicht auffindbar", Toast.LENGTH_LONG).show()
    }

    /** Ordner-URI zur Datei — Dokumente oder Medien-Speicher. */
    private fun ordnerUri(uri: android.net.Uri): android.net.Uri? {
        // 1) Dokument aus der Dateiauswahl: Elternordner bauen
        try {
            if (android.provider.DocumentsContract.isDocumentUri(this, uri)) {
                val id = android.provider.DocumentsContract.getDocumentId(uri)
                val schnitt = id.lastIndexOf('/')
                if (schnitt > 0) {
                    return android.provider.DocumentsContract.buildDocumentUri(
                        uri.authority ?: return null, id.substring(0, schnitt))
                }
            }
        } catch (fehler: Throwable) {
        }
        // 2) Eigenes Ergebnis: Ordner aus dem Medien-Speicher lesen
        val pfad = medienOrdner(uri) ?: return null
        return try {
            android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:" + pfad.trimEnd('/'))
        } catch (fehler: Throwable) {
            null
        }
    }

    /** Ordner (z. B. „Pictures" oder „Download") einer Medien-Datei. */
    private fun medienOrdner(uri: android.net.Uri): String? {
        return try {
            val spalten = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                arrayOf(android.provider.MediaStore.MediaColumns.DATA)
            }
            contentResolver.query(uri, spalten, null, null, null)?.use { zeiger ->
                if (!zeiger.moveToFirst()) return null
                val wert = zeiger.getString(0) ?: return null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    wert.trimEnd('/')
                } else {
                    wert.substringBeforeLast('/').removePrefix("/storage/emulated/0/")
                }
            }
        } catch (fehler: Throwable) {
            null
        }
    }

    /** Öffnet eine Datei in einer anderen App. */
    private fun oeffneExtern(uri: android.net.Uri, typ: String) {
        try {
            val absicht = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, typ)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(absicht)
        } catch (fehler: Exception) {
            Toast.makeText(this, "Keine App zum Öffnen", Toast.LENGTH_LONG).show()
        }
    }

    private fun oeffneLetzte() {
        val uri = letzteDatei ?: return
        try {
            val absicht = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, letzterTyp)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(absicht)
        } catch (fehler: Exception) {
            Toast.makeText(this, "Keine App zum Oeffnen: " + uri, Toast.LENGTH_LONG).show()
        }
    }

    /** Abstuerze mitschreiben: danach liegt der Grund in filesDir/absturz.txt. */
    private fun abstuerzeMitschreiben() {
        val vorher = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { faden, fehler ->
            try {
                val text = fehler.stackTraceToString()
                java.io.File(filesDir, "absturz.txt").writeText(text)
            } catch (_: Exception) {
            }
            vorher?.uncaughtException(faden, fehler)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        abstuerzeMitschreiben()
        super.onCreate(savedInstanceState)
        Darstellung.anwenden(this)

        val rahmen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val titel = TextView(this).apply {
            text = "Text schwärzen"
            textSize = 22f
        }
        bericht = TextView(this).apply {
            setTextColor(Darstellung.farben(this@MainActivity).text)
            text = "Ein Bild wählen — erkannt wird der Text darauf, nicht das Bild."
            textSize = 15f
            setPadding(0, 12, 0, 12)
        }
        bildAnzeige = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(0, 12, 0, 12)
            // Antippen öffnet das letzte Ergebnis — die Vorschau bleibt sonst
            // als „letzte Bearbeitung" stehen, ohne daß man etwas tun kann
            // (Julius, 25.11.2026).
            setOnClickListener {
                if (letzteDatei != null) {
                    oeffneLetzte()
                } else {
                    Toast.makeText(this@MainActivity, "Noch kein Ergebnis",
                                   Toast.LENGTH_SHORT).show()
                }
            }
        }
        bildWaehlen = Button(this).apply {
            text = "Datei wählen"
            // Ein Knopf für Bild, PDF und Text (Julius, 24.11.2026). Die Art wird an
            // Inhalt und Endung erkannt; deshalb sind alle Typen zugelassen.
            setOnClickListener {
                // Mehrere gemischte Typen führten auf dem Gerät zu einer leeren Liste
                // („Bilder sind nicht auswählbar", 25.11.2026). Deshalb alle Dateien
                // anzeigen — die Art wird danach an Inhalt und Endung erkannt.
                textAuswahl.launch(arrayOf("*/*"))
            }
        }
        val letzteKnopf = Button(this).apply {
            text = "Letzte Dateien"
            setOnClickListener { letzteZeigen() }
        }
        val ergebnisKnopf = Button(this).apply {
            text = "Ergebnisse"
            setOnClickListener { ergebnisseZeigen() }
        }
        val schriftKnopf = Button(this).apply {
            text = "Schrift: Lateinisch"
            setOnClickListener {
                // In diesem Block ist "this" der Knopf — für den Dialog braucht es die Activity.
                val schriften = Erkennung.Schrift.values()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Schriftsystem wählen")
                    .setItems(schriften.map { it.anzeige }.toTypedArray()) { _, welcher ->
                        erkennung.setzeSchrift(schriften[welcher])
                        this.text = "Schrift: " + schriften[welcher].anzeige.substringBefore(" ")
                        bericht.text = "Schrift: " + schriften[welcher].anzeige
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }
        val selbsttest = Button(this).apply {
            text = "Selbsttest"
            setOnClickListener { selbsttestLaufen() }
        }
        val darstellungKnopf = Button(this).apply {
            text = "Darstellung"
            setOnClickListener { darstellungWaehlen() }
        }
        val infoKnopf = Button(this).apply {
            text = "Info"
            setOnClickListener { infoZeigen() }
        }
        val beenden = Button(this).apply {
            text = "Beenden"
            setOnClickListener {
                threads.shutdown()
                finish()
            }
        }
        speichern = Button(this).apply {
            text = "Speichern"
            isEnabled = false
            visibility = android.view.View.GONE
            setOnClickListener { speichere() }
        }

        // Reihenfolge nach Julius' Wunsch (24.11.2026): Bild, PDF, Text,
        // Letzte Dateien, Selbsttest, Beenden. „Bereich ziehen" und „Schwärzen"
        // sind keine eigenen Knöpfe mehr — sie stehen im Dialog nach der Auswahl.
        // Keine Ueberfrachtung (25.11.2026): eine Hauptaktion, zwei Listen,
        // alles Seltene hinter einem Knopf.
        val mehrKnopf = Button(this).apply {
            text = "Mehr..."
            setOnClickListener { mehrZeigen() }
        }
        rahmen.addView(titel)
        rahmen.addView(bericht)
        rahmen.addView(bildWaehlen)
        rahmen.addView(letzteKnopf)
        rahmen.addView(ergebnisKnopf)
        rahmen.addView(mehrKnopf)
        rahmen.addView(ScrollView(this).apply { addView(bildAnzeige) })

        setContentView(rahmen)
        // Einheitliches Aussehen: runde Knöpfe, Akzentfarbe, Abstände
        for (i in 0 until rahmen.childCount) {
            (rahmen.getChildAt(i) as? Button)?.let { Darstellung.knopf(it, this) }
        }
        Darstellung.titel(titel, this)
        zeigeLetztenAbsturz()
    }

    /** Schreibt den Absturz zusätzlich nach Downloads, damit er sich anhängen läßt. */
    private fun schreibeAbsturzNachDownloads(text: String): android.net.Uri? {
        return try {
            val name = "absturz-" + System.currentTimeMillis() + ".txt"
            val werte = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val ziel = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, werte)
            ziel?.let {
                contentResolver.openOutputStream(it)?.use { strom ->
                    strom.write(text.toByteArray(Charsets.UTF_8))
                }
            }
            ziel
        } catch (fehler: Throwable) {
            null
        }
    }

    /** Liegt ein Absturzbericht vor, wird er beim Start gezeigt (statt still zu bleiben). */
    private fun zeigeLetztenAbsturz() {
        val datei = java.io.File(filesDir, "absturz.txt")
        if (!datei.exists()) return
        val text = try {
            datei.readText().takeLast(1200)
        } catch (fehler: Throwable) {
            return
        }
        // Julius' Frage (25.11.2026): „wie soll ich den text schicken" — deshalb
        // Kopieren, Teilen und eine Datei in Downloads, die sich anhängen läßt.
        val berichtDatei = schreibeAbsturzNachDownloads(text)
        AlertDialog.Builder(this)
            .setTitle("Letzter Absturz")
            .setMessage(text)
            .setPositiveButton("Kopieren") { _, _ ->
                val ablage = getSystemService(android.content.ClipboardManager::class.java)
                ablage.setPrimaryClip(android.content.ClipData.newPlainText("Absturz", text))
                Toast.makeText(this, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Teilen") { _, _ ->
                val absicht = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Absturz Text schwärzen")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(absicht, "Absturz schicken"))
            }
            .setNegativeButton("Löschen") { _, _ -> datei.delete() }
            .show()
    }

    /** Alles Seltene an einem Ort statt auf acht Knoepfen. */
    private fun mehrZeigen() {
        AlertDialog.Builder(this)
            .setTitle("Mehr")
            .setItems(arrayOf("Schrift wählen", "Darstellung", "Selbsttest", "Info",
                              "Beenden")) { _, was ->
                when (was) {
                    0 -> schriftWaehlen()
                    1 -> darstellungWaehlen()
                    2 -> selbsttestLaufen()
                    3 -> infoZeigen()
                    else -> { threads.shutdown(); finish() }
                }
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun schriftWaehlen() {
        val schriften = Erkennung.Schrift.values()
        AlertDialog.Builder(this)
            .setTitle("Schriftsystem wählen")
            .setItems(schriften.map { it.anzeige }.toTypedArray()) { _, welcher ->
                erkennung.setzeSchrift(schriften[welcher])
                bericht.text = "Schrift: " + schriften[welcher].anzeige
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /** Hell, Dunkel oder wie das Gerät. */
    private fun darstellungWaehlen() {
        val namen = arrayOf("Wie das Gerät", "Hell", "Dunkel")
        val reihenfolge = arrayOf(Darstellung.Modus.SYSTEM, Darstellung.Modus.HELL,
                                  Darstellung.Modus.DUNKEL)
        val jetzt = reihenfolge.indexOf(Darstellung.modus(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Darstellung")
            .setItems(arrayOf("Farbschema: " + namen[jetzt],
                              "Stil: " + Darstellung.stil(this).anzeige)) { _, was ->
                if (was == 0) {
                    AlertDialog.Builder(this)
                        .setTitle("Farbschema")
                        .setSingleChoiceItems(namen, jetzt) { _, welcher ->
                            Darstellung.setzeModus(this, reihenfolge[welcher])
                            recreate()
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                } else {
                    val stile = Darstellung.Stil.values()
                    AlertDialog.Builder(this)
                        .setTitle("Stil")
                        .setItems(stile.map { it.anzeige }.toTypedArray()) { _, welcher ->
                            Darstellung.setzeStil(this, stile[welcher])
                            recreate()
                        }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    /** „Info": was die App tut, was sie kann, was sie nicht kann. */
    private fun infoZeigen() {
        val fassung = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (fehler: Exception) {
            "?"
        }
        val text = """
            Text schwärzen · Fassung $fassung

            Schwärzt Text in Bildern, PDFs und Textdateien — auf dem Gerät, ohne Netz.

            ERKENNUNG
            ML Kit mit fünf Schriftsystemen: Lateinisch (deutsch, englisch, romanische
            Sprachen), Chinesisch, Japanisch, Koreanisch, Devanagari (Hindi u. a.).
            Kyrillisch, Griechisch, Arabisch, Hebräisch kann ML Kit nicht.

            SCHWÄRZEN
            · Nur Adressen und Namen — nach Regeln (E-Mail, Telefon, IBAN, Adressen in
              deutschen, romanischen und englischen Dokumenten, Buchungsnummern)
            · Alles — jede erkannte Zeile
            · Bereich ziehen — Rahmen um einen Fleck (z. B. QR-Code)
            · Zeilen auswählen — bei Textdateien
            QR- und Strichcodes werden immer mitgeschwärzt.

            WAS DIE APP NICHT MACHT
            Sie löscht keine Bilder und schickt nichts ins Netz. Sie ersetzt keine
            Beratung: ein Blick auf das Ergebnis bleibt nötig.

            Quelltext: github.com/Suilui1/text-schwaerzen
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Info")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }

    /** Wie bei PDF und Text: nach dem Laden fragen, was geschwärzt werden soll. */
    private fun bildGewaehlt() {
        if (original == null) return
        AlertDialog.Builder(this)
            .setTitle("Wie stark schwärzen?")
            .setItems(arrayOf("Nur Adressen und Namen", "Alles", "Bereich ziehen")) { _, welcher ->
                when (welcher) {
                    0 -> { modus = "gezielt"; arbeite(original!!) }
                    1 -> { modus = "alles"; arbeite(original!!) }
                    else -> rahmenZiehen()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun schwaezen() {
        val bild = original ?: return
        AlertDialog.Builder(this)
            .setTitle("Wie stark schwärzen?")
            .setItems(arrayOf("Nur Adressen und Namen", "Alles")) { _, welcher ->
                modus = if (welcher == 0) "gezielt" else "alles"
                arbeite(bild)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun arbeite(bild: Bitmap) {
        bericht.text = "Erkennung läuft …"
        bildWaehlen.isEnabled = false
        threads.execute {
            val ergebnisBild = schwaerzen(bild, erkennung, modus)
            val neu = ergebnisBild.bild
            val zaehler = ergebnisBild.zaehler
            runOnUiThread {
                fertig = neu
                bildAnzeige.setImageBitmap(neu)
                val teile = if (zaehler.isEmpty()) "nichts Sensibles gefunden"
                else zaehler.entries.joinToString(", ") {
                    "${Regeln.bezeichnung(it.key)} ${it.value}"
                }
                val text = if (ergebnisBild.woerter == 0)
                    "Kein Text erkannt (" + ergebnisBild.woerter + " Woerter). " +
                    "Bitte ein groesseres Bild verwenden."
                else teile + "\n\n" + ergebnisBild.woerter + " Woerter erkannt"
                bericht.text = text + "\n\nVorschau antippen öffnet die Datei."
                bildWaehlen.isEnabled = true
                // Wie beim PDF: sofort speichern und denselben Abschlussdialog zeigen
                // (Julius: „beim speichern von bildern anders, sollte wie bei pdf sein").
                speichereUndZeige(neu, text)
            }
        }
    }

    /** Speichert das Bild und zeigt danach denselben Dialog wie beim PDF. */
    private fun speichereUndZeige(bild: Bitmap, meldung: String) {
        threads.execute {
            val name = "$quelName-geschwaerzt.png"
            val werte = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
            }
            val ziel = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, werte)
            if (ziel != null) {
                contentResolver.openOutputStream(ziel)?.use { strom ->
                    bild.compress(Bitmap.CompressFormat.PNG, 100, strom)
                }
                letzteDatei = ziel
                letzterTyp = "image/png"
                merken(ziel, name, "bild-aus")
            }
            runOnUiThread {
                val hinweis = if (ziel != null) "$meldung\n\nGespeichert: Bilder/$name"
                else "$meldung\n\nSpeichern fehlgeschlagen"
                bericht.text = hinweis
                abschlussDialog(if (ziel != null) "Geschwaerzt" else "Fehler", hinweis)
            }
        }
    }

    private fun speichere() {
        val bild = fertig ?: return
        speichereUndZeige(bild, "Geschwaerzt")
        if (true) return
        threads.execute {
            val name = "$quelName-geschwaerzt.png"
            val werte = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
            }
            val ziel = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, werte)
            ziel?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { strom ->
                    bild.compress(Bitmap.CompressFormat.PNG, 100, strom)
                }
                letzteDatei = uri
                letzterTyp = "image/png"
            }
            runOnUiThread {
                val text = if (ziel != null) "Gespeichert: Bilder/$name" else "Speichern fehlgeschlagen"
                bericht.text = text
                AlertDialog.Builder(this)
                    .setTitle(if (ziel != null) "Gespeichert" else "Fehler")
                    .setMessage(text)
                    .setPositiveButton("Oeffnen") { _, _ -> oeffneLetzte() }
                    .setNegativeButton("OK", null)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        erkennung.schliessen()
        threads.shutdown()
        super.onDestroy()
    }
}
