package de.juliusk.textschwaerzen

/**
 * Die Regeln — dieselben wie auf dem Rechner (textschwaerzen/regeln.py).
 *
 * Warum eine zweite Fassung: Android braucht Kotlin, Python läuft dort nicht.
 * Die Muster sind absichtlich gleich gehalten, damit beide Programme dieselben Zeilen
 * erwischen. Messungen vom 23./24.11.2026 gelten unverändert.
 */
object Regeln {

    private val MAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val IBAN = Regex("\\b[A-Z]{2}[0-9]{2}(?:[ ]?[0-9]{4}){3,7}[0-9]{0,2}\\b")
    private val TELEFON = Regex("\\+?[0-9][0-9 /\\-().]{6,}[0-9]")
    private val SCHLUESSEL = Regex(
        "\\b(?:ghp_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9]{20,}|AKIA[A-Z0-9]{12,}" +
            "|AIza[A-Za-z0-9_-]{20,}|[A-Za-z0-9]{44,})\\b"
    )
    private val KOPF = Regex(
        "^(?:von|an|betreff|cc|bcc|from|to|subject|datum|date|gesendet|sent)\\s*[:：]",
        RegexOption.IGNORE_CASE
    )
    private val GRUSS = Regex(
        "^(?:mit freundlichen|freundliche|viele|beste|herzliche|kind regards" +
            "|best regards|regards|cheers)",
        RegexOption.IGNORE_CASE
    )
    private val BEGRIFF = Regex(
        "(?i)(login[- ]?code|best[aä]tigungscode|code|kundennummer|kundennr|bestellnummer" +
            "|rechnungsnummer|auftragsnummer|vertragsnummer|pin|tan|passwort|kennwort" +
            "|zugangsnummer)"
    )
    private val NUR_ZAHLEN = Regex("^[0-9][0-9 .\\-/]{4,}[0-9]$")
    // Adressen und Buchungsnummern fehlten — auf einer Fahrkarte blieben Straße und
    // Postleitzahl stehen (Julius, 24.11.2026).
    private val STRASSE = Regex(
        "\\b[A-Za-zÄÖÜäöüß\\-]{3,}(?:straße|strasse|str\\.|weg|gasse|platz|allee|ring|damm" +
            "|ufer|steig|graben|hof|park|chaussee)\\s*\\d+[a-zA-Z]?\\b",
        RegexOption.IGNORE_CASE
    )
    // Romanische Sprachen: Calle, rue, Via, Rua … (24.11.2026)
    private val STRASSE_ROMANISCH = Regex(
        "\\b(?:calle|avenida|avda|plaza|paseo|camino|carrer|rue|avenue|boulevard|place" +
            "|chemin|impasse|via|viale|piazza|corso|largo|rua|praça|praca|travessa|strada" +
            "|bulevardul|calea|str\\.)\\s+[A-Za-zÀ-ÿ'.\\- ]{2,}\\s*\\d+[A-Za-z]?\\b",
        RegexOption.IGNORE_CASE
    )
    private val STRASSE_ZAHL_VORN = Regex(
        "\\b\\d{1,4}[a-zA-Z]?\\s+(?:rue|avenue|boulevard|place|chemin|impasse|quai|allée)\\b",
        RegexOption.IGNORE_CASE
    )
    // Englisch: „12 Baker Street", „Baker Street 12" und britische Postleitzahl
    private val STRASSE_ENGLISCH = Regex(
        "\\b(?:\\d{1,4}[a-zA-Z]?\\s+[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*\\s+" +
            "(?:Street|Road|Lane|Avenue|Close|Drive|Way|Place|Square|Court|Terrace" +
            "|Gardens|Hill|Park|Crescent|Grove|Mews)\\b" +
            "|[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*\\s+" +
            "(?:Street|Road|Lane|Avenue|Close|Drive|Way|Place|Square|Court|Terrace" +
            "|Gardens|Hill|Park|Crescent|Grove|Mews)\\s*\\d{1,4}[a-zA-Z]?\\b)"
    )
    private val PLZ_ENGLISCH = Regex("\\b[A-Z]{1,2}\\d[A-Z\\d]?\\s*\\d[A-Z]{2}\\b")
    private val PLZ_ROMANISCH = Regex("\\b(?:\\d{4}-\\d{3}|\\d{5,6})\\s+[A-ZÀ-Þ][\\wÀ-ÿ'\\-]{2,}")
    private val PLZ_ORT = Regex("\\b\\d{5}\\s+[A-ZÄÖÜ][a-zäöüß\\-]+(?:\\s+[A-Za-zÄÖÜäöüß\\-]+)*\\b")
    private val BUCHUNG = Regex(
        "(?i)(buchungsnummer|buchungscode|bestellnummer|auftragsnummer|fahrkarte" +
            "|ticketnummer|referenznummer|belegnummer)\\s*[:#]?\\s*[A-Za-z0-9#\\-]+"
    )
    private val KLEINER_NAME = Regex("\\b[a-zäöüß]{3,}\\s+[a-zäöüß]{3,}(?:\\s+[a-zäöüß]{3,})?\\b")
    private val FUNKTIONSWOERTER = setOf(
        "der", "die", "das", "den", "dem", "des", "und", "oder", "mit", "von", "für", "fur",
        "auf", "aus", "bei", "nach", "über", "uber", "unter", "vor", "zu", "zum", "zur",
        "ist", "sind", "wird", "werden", "hat", "haben", "nicht", "auch", "sich", "sie",
        "wir", "ich", "bitte", "dort", "hier", "dann", "noch", "nur", "sowie", "alle",
        "diese", "dieser", "dieses", "eine", "einen", "einem", "einer", "eines", "ein"
    )

    fun siehtNachNameAus(text: String): Boolean {
        val roh = text.trim()
        if (roh.isEmpty() || roh.contains("@") || roh.contains(":")) return false
        if (roh.any { it.isDigit() }) return false
        val woerter = roh.split(" ").filter { it.isNotEmpty() }
        if (woerter.size !in 2..5) return false
        return woerter.all { it.first().isUpperCase() }
    }

    /** Namen stehen manchmal mitten in einer Zeile („AB 14:00 julius krause"). */
    fun enthaeltKleinenNamen(text: String): Boolean {
        for (treffer in KLEINER_NAME.findAll(text)) {
            val woerter = treffer.value.split(" ").filter { it.isNotEmpty() }
            if (woerter.all { it !in FUNKTIONSWOERTER }) return true
        }
        return false
    }

    /** Was steckt in dieser Zeile? Leer = nicht schwärzen. */
    fun treffer(text: String, modus: String): List<String> {
        if (modus == "alles") return listOf("alles")
        if (modus != "gezielt") return emptyList()
        val arten = mutableListOf<String>()
        if (MAIL.containsMatchIn(text)) arten.add("mail")
        if (IBAN.containsMatchIn(text)) arten.add("iban")
        if (TELEFON.containsMatchIn(text)) arten.add("telefon")
        if (SCHLUESSEL.containsMatchIn(text)) arten.add("schluessel")
        if (KOPF.containsMatchIn(text)) arten.add("kopf")
        if (GRUSS.containsMatchIn(text)) arten.add("gruss")
        if (BEGRIFF.containsMatchIn(text)) arten.add("code")
        if (NUR_ZAHLEN.matches(text) && text.count { it.isDigit() } >= 5) arten.add("nummer")
        if (STRASSE.containsMatchIn(text) || STRASSE_ROMANISCH.containsMatchIn(text)
            || STRASSE_ZAHL_VORN.containsMatchIn(text)
            || STRASSE_ENGLISCH.containsMatchIn(text)) arten.add("strasse")
        if (PLZ_ORT.containsMatchIn(text) || PLZ_ROMANISCH.containsMatchIn(text)
            || PLZ_ENGLISCH.containsMatchIn(text)) arten.add("plz")
        if (BUCHUNG.containsMatchIn(text)) arten.add("buchung")
        if (arten.isEmpty() && (siehtNachNameAus(text) || enthaeltKleinenNamen(text))) {
            arten.add("name")
        }
        // Jede Art nur einmal (sonst steht „PLZ" zweimal im Bericht).
        val eindeutig = mutableListOf<String>()
        for (art in arten) if (!eindeutig.contains(art)) eindeutig.add(art)
        return eindeutig
    }

    fun bezeichnung(schluessel: String): String = when (schluessel) {
        "mail" -> "E-Mail"
        "iban" -> "IBAN"
        "telefon" -> "Telefon"
        "schluessel" -> "Schlüssel"
        "kopf" -> "Kopfzeile"
        "gruss" -> "Grußzeile"
        "name" -> "Name"
        "code" -> "Code oder Nummer"
        "nummer" -> "Nummer"
        "strasse" -> "Straße"
        "plz" -> "Postleitzahl"
        "buchung" -> "Buchungsnummer"
        "barcode" -> "QR-/Strichcode"
        "alles" -> "alles"
        else -> schluessel
    }
}
