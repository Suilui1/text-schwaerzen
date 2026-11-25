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
    private lateinit var pdfWaehlen: Button
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

    /** Merkt sich die zuletzt geöffneten Dateien (höchstens acht). */
    private fun merken(uri: android.net.Uri, name: String, typ: String) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        val alt = verlauf.getStringSet("dateien", emptySet())?.toMutableSet()
            ?: mutableSetOf<String>()
        alt.removeAll { it.split("|").getOrNull(1) == uri.toString() }
        alt.add(typ + "|" + uri + "|" + name)
        val gekuerzt = alt.toList().takeLast(8).toSet()
        verlauf.edit().putStringSet("dateien", gekuerzt).apply()
    }

    /** Zeigt die zuletzt geöffneten Bilder und Dokumente. */
    private fun letzteZeigen() {
        val eintraege = (verlauf.getStringSet("dateien", emptySet()) ?: emptySet())
            .toList().sortedDescending()
        if (eintraege.isEmpty()) {
            Toast.makeText(this, "Noch keine Datei geöffnet", Toast.LENGTH_SHORT).show()
            return
        }
        val namen = eintraege.map { teil ->
            val stuecke = teil.split("|")
            when (stuecke[0]) {
                "pdf" -> "PDF   "
                "text" -> "Text  "
                "bild" -> "Bild  "
                else -> "fertig "
            } + stuecke.getOrElse(2) { "?" }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Letzte Dateien")
            .setItems(namen) { _, welcher ->
                val stuecke = eintraege[welcher].split("|")
                val uri = android.net.Uri.parse(stuecke[1])
                when (stuecke[0]) {
                    "pdf" -> pdfGewaehlt(uri)
                    "text" -> textGewaehlt(uri)
                    "bild" -> ladeBild(uri)
                    // Geschwärzte Ergebnisse werden nur geöffnet, nicht neu verarbeitet.
                    "pdf-aus" -> oeffneExtern(uri, "application/pdf")
                    "text-aus" -> oeffneExtern(uri, "text/plain")
                    else -> oeffneExtern(uri, "image/png")
                }
            }
            .setNegativeButton("Schliessen", null)
            .show()
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

    private val bereichAuswahl = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { ergebnis ->
        if (ergebnis.resultCode != RESULT_OK) {
            bildWaehlen.isEnabled = true
            return@registerForActivityResult
        }
        val d = ergebnis.data ?: return@registerForActivityResult
        bereichSchwaerzen(d.getFloatExtra("x0", 0f), d.getFloatExtra("y0", 0f),
                          d.getFloatExtra("x1", 0f), d.getFloatExtra("y1", 0f))
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
                                .setPositiveButton("Gut", null).show()
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
                .setMessage("Datei nicht lesbar").setPositiveButton("Gut", null).show()
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
                                .setMessage(ergebnis.fehler).setPositiveButton("Gut", null).show()
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
                    .setPositiveButton("Gut", null)
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
                BereichActivity.uebergebenesBild = klein
                runOnUiThread {
                    bildWaehlen.isEnabled = true
                    bericht.text = ""
                    val absicht = Intent(this, BereichActivity::class.java)
                    bereichAuswahl.launch(absicht)
                }
            } catch (fehler: Exception) {
                runOnUiThread {
                    bildWaehlen.isEnabled = true
                    AlertDialog.Builder(this).setTitle("Fehler")
                        .setMessage(fehler.message).setPositiveButton("Gut", null).show()
                }
            }
        }
    }

    private fun bereichSchwaerzen(x0: Float, y0: Float, x1: Float, y1: Float) {
        // Kam der Aufruf aus dem PDF-Weg, wird der Rahmen auf jede Seite angewendet.
        val pdf = pdfMitRahmen
        if (pdf != null) {
            pdfMitRahmen = null
            val rahmen = android.graphics.RectF(x0, y0, x1, y1)
            bericht.text = "PDF wird verarbeitet …"
            pdfWaehlen.isEnabled = false
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
                        pdfWaehlen.isEnabled = true
                        bericht.text = "Abgebrochen."
                        Toast.makeText(this, "Abgebrochen — nichts gespeichert",
                                       Toast.LENGTH_LONG).show()
                    }
                    return@execute
                }
                runOnUiThread {
                    pdfWaehlen.isEnabled = true
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
                        .setPositiveButton("Gut", null).show()
                    return@runOnUiThread
                }
                bericht.text = ""
                val absicht = Intent(this, BereichActivity::class.java)
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
        pdfWaehlen.isEnabled = false
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
                    pdfWaehlen.isEnabled = true
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
                        .setPositiveButton("Gut", null)
                        .show()
                    bildWaehlen.isEnabled = true
                    pdfWaehlen.isEnabled = true
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
                pdfWaehlen.isEnabled = true
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
        AlertDialog.Builder(this)
            .setTitle(titel)
            .setMessage(text)
            .setPositiveButton("Oeffnen") { _, _ -> oeffneLetzte() }
            .setNeutralButton("Speichern unter…") { _, _ ->
                val name = if (letzterTyp == "application/pdf")
                    nameDerDatei(letzteDatei ?: android.net.Uri.EMPTY) + ".pdf"
                else "geschwaerzt.png"
                if (letzterTyp == "application/pdf") speichernPdf.launch("geschwaerzt.pdf")
                else speichernBild.launch("geschwaerzt.png")
            }
            .setNegativeButton("Teilen") { _, _ -> teileLetzte() }
            .show()
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

        val rahmen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val titel = TextView(this).apply {
            text = "Text schwärzen"
            textSize = 22f
        }
        bericht = TextView(this).apply {
            text = "Ein Bild wählen — erkannt wird der Text darauf, nicht das Bild."
            textSize = 15f
            setPadding(0, 12, 0, 12)
        }
        bildAnzeige = ImageView(this).apply {
            adjustViewBounds = true
            setPadding(0, 12, 0, 12)
        }
        bildWaehlen = Button(this).apply {
            text = "Datei wählen"
            // Ein Knopf für Bild, PDF und Text (Julius, 24.11.2026). Die Art wird an
            // Inhalt und Endung erkannt; deshalb sind alle Typen zugelassen.
            setOnClickListener {
                textAuswahl.launch(arrayOf("image/*", "application/pdf", "application/x-pdf",
                                           "text/plain", "text/*",
                                           "application/octet-stream"))
            }
        }
        val letzteKnopf = Button(this).apply {
            text = "Letzte Dateien"
            setOnClickListener { letzteZeigen() }
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
        rahmen.addView(titel)
        rahmen.addView(bericht)
        rahmen.addView(bildWaehlen)
        rahmen.addView(letzteKnopf)
        rahmen.addView(schriftKnopf)
        rahmen.addView(selbsttest)
        rahmen.addView(infoKnopf)
        rahmen.addView(beenden)
        rahmen.addView(ScrollView(this).apply { addView(bildAnzeige) })

        setContentView(rahmen)
        zeigeLetztenAbsturz()
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
        AlertDialog.Builder(this)
            .setTitle("Letzter Absturz")
            .setMessage(text)
            .setPositiveButton("Löschen") { _, _ -> datei.delete() }
            .setNegativeButton("Behalten", null)
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
            .setPositiveButton("Gut", null)
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
                bericht.text = text
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
                    .setNegativeButton("Gut", null)
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
