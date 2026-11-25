package de.juliusk.textschwaerzen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Selbstprüfung ohne Handy: prüft die Regeln, die Textdatei-Logik und die Dateierkennung.
 *
 * Anlaß (25.11.2026): Julius mußte Fehler finden, die ein Test gefunden hätte —
 * „das ist mühsam, kannst du eine Selbstprüfung bauen bevor ich teste".
 */
class PruefungTest {

    private fun arten(text: String) = Regeln.treffer(text, "gezielt")

    @Test
    fun deutscheAdresseWirdErkannt() {
        val arten = arten("Julius Krause, Bismarckallee 3, 79093 Freiburg")
        assertTrue("Straße fehlt: $arten", arten.contains("strasse"))
        assertTrue("PLZ fehlt: $arten", arten.contains("plz"))
    }

    @Test
    fun franzoesischeAdresseWirdErkannt() {
        val arten = arten("Jean Dupont, 12 rue de Rivoli, 75001 Paris")
        assertTrue("Straße fehlt: $arten", arten.contains("strasse"))
        assertTrue("PLZ fehlt: $arten", arten.contains("plz"))
    }

    @Test
    fun englischeAdresseWirdErkannt() {
        val arten = arten("John Miller, 12 Baker Street, London SW1A 1AA")
        assertTrue("Straße fehlt: $arten", arten.contains("strasse"))
        assertTrue("PLZ fehlt: $arten", arten.contains("plz"))
    }

    @Test
    fun postleitzahlStehtNurEinmalImBericht() {
        val arten = arten("Juan Pérez, Calle Mayor 12, 28013 Madrid")
        assertEquals("Doppelte Art: $arten", 1, arten.count { it == "plz" })
    }

    @Test
    fun harmloserSatzBleibtUnangetastet() {
        assertTrue("Zu viel geschwärzt", arten("Das ist ein harmloser Satz.").isEmpty())
    }

    @Test
    fun telefonUndMailSindSprachunabhaengig() {
        assertTrue(arten("Phone: +44 20 7946 0958").contains("telefon"))
        assertTrue(arten("Email: john.miller@example.com").contains("mail"))
    }

    @Test
    fun textdateiWirdZeilenweiseGeschwaerzt() {
        val inhalt = "Julius Krause\nMail: julius@example.org\nDer Termin ist am Dienstag.\n"
        val (neu, zaehler, getroffen) = TextVerarbeitung.schwaerzeInhalt(inhalt, "gezielt")
        assertEquals(99, getroffen)   // ABSICHTLICH FALSCH — prüft, ob die Sperre greift
        assertTrue("Mail nicht erkannt: $zaehler", zaehler.containsKey("mail"))
        assertTrue("Balken fehlen", neu.contains("█"))
        assertTrue("Harmlose Zeile verändert", neu.contains("Der Termin ist am Dienstag."))
    }

    @Test
    fun allesSchwaerztJedeZeile() {
        val (_, _, getroffen) = TextVerarbeitung.schwaerzeInhalt("eins\nzwei\ndrei\n", "alles")
        assertEquals(3, getroffen)
    }

    @Test
    fun dateierkennungUnterscheidetTextPdfUndBild() {
        assertEquals(TextVerarbeitung.Art.TEXT, TextVerarbeitung.artBestimmen("notiz.txt", "text/plain"))
        assertEquals(TextVerarbeitung.Art.TEXT, TextVerarbeitung.artBestimmen("liste", "text/csv"))
        assertEquals(TextVerarbeitung.Art.PDF, TextVerarbeitung.artBestimmen("ticket.pdf", "application/pdf"))
        assertEquals(TextVerarbeitung.Art.BILD, TextVerarbeitung.artBestimmen("foto.jpg", "image/jpeg"))
        // Der Fehler vom 25.11.2026: ohne Endung wurde .txt als Bild behandelt
        assertEquals(TextVerarbeitung.Art.TEXT, TextVerarbeitung.artBestimmen("notizen.TXT", ""))
    }
}
