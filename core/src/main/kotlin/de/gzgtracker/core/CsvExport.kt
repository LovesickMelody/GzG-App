package de.gzgtracker.core

import java.time.LocalDate

/**
 * Export der Liste als CSV.
 *
 * Trennzeichen ist das Semikolon und die Datei beginnt mit einer BOM: So oeffnet
 * Excel in deutscher Spracheinstellung die Datei ohne Importdialog und mit
 * korrekten Umlauten. Betraege stehen mit Komma als Dezimaltrenner, damit sie in
 * der Tabelle direkt als Zahl ankommen.
 */
object CsvExport {

    const val TRENNZEICHEN = ";"
    const val BOM = "\uFEFF"

    private val SPALTEN = listOf(
        "Produkt",
        "Aktion",
        "Marke",
        "Konto",
        "Status",
        "Kaufdatum",
        "Haendler",
        "EAN",
        "Kaufpreis",
        "Erwartete Erstattung",
        "Erstatteter Betrag",
        "Eingereicht am",
        "Erstattet am",
        "Notiz",
    )

    fun erzeuge(
        submissions: List<Submission>,
        actionsById: Map<String, PromoAction>,
        accountsById: Map<Long, Account>,
    ): String {
        val zeilen = StringBuilder()
        zeilen.append(BOM)
        zeilen.append(SPALTEN.joinToString(TRENNZEICHEN))
        zeilen.append("\r\n")

        submissions.forEach { submission ->
            val action = actionsById[submission.actionId]
            val felder = listOf(
                submission.productName,
                action?.title ?: submission.actionId,
                action?.brand.orEmpty(),
                accountsById[submission.accountId]?.name.orEmpty(),
                statusText(submission.status),
                datum(submission.purchaseDate),
                submission.retailer.orEmpty(),
                submission.ean.orEmpty(),
                Money.formatPlain(submission.pricePaidCents),
                Money.formatPlain(TotalsCalculator.erwarteteErstattungCents(submission, action)),
                submission.refundedAmountCents?.let(Money::formatPlain).orEmpty(),
                datum(submission.submittedAt),
                datum(submission.refundedAt),
                submission.note.orEmpty(),
            )
            zeilen.append(felder.joinToString(TRENNZEICHEN, transform = ::maskiere))
            zeilen.append("\r\n")
        }

        return zeilen.toString()
    }

    private fun statusText(status: SubmissionStatus): String = when (status) {
        SubmissionStatus.GEKAUFT -> "Gekauft"
        SubmissionStatus.EINGEREICHT -> "Eingereicht"
        SubmissionStatus.ERSTATTET -> "Erstattet"
        SubmissionStatus.ABGELEHNT -> "Abgelehnt"
    }

    private fun datum(wert: LocalDate?): String = wert?.let {
        "%02d.%02d.%04d".format(it.dayOfMonth, it.monthValue, it.year)
    }.orEmpty()

    /**
     * Feldern mit Trennzeichen, Anfuehrungszeichen oder Zeilenumbruch werden
     * Anfuehrungszeichen verpasst, innere verdoppelt (RFC 4180).
     *
     * Zusaetzlich wird ein fuehrendes `=`, `+`, `-` oder `@` entschaerft: Tabellen-
     * programme wuerden solche Zellen sonst als Formel ausfuehren. Ein Produktname
     * aus dem Netz soll in der eigenen Tabelle nichts starten koennen.
     */
    private fun maskiere(feld: String): String {
        val entschaerft = if (feld.istFormel()) "'$feld" else feld
        val brauchtAnfuehrung = entschaerft.any { zeichen ->
            zeichen == ';' || zeichen == '"' || zeichen == '\n' || zeichen == '\r'
        }
        return if (brauchtAnfuehrung) {
            "\"${entschaerft.replace("\"", "\"\"")}\""
        } else {
            entschaerft
        }
    }

    private val FORMEL_START = setOf('=', '+', '-', '@')

    /**
     * Ein fuehrendes Minus macht ein Feld nicht zur Formel, wenn dahinter eine Zahl
     * steht — sonst wuerde "-3,99" als Text in der Tabelle landen statt als Betrag.
     */
    private fun String.istFormel(): Boolean {
        if (isEmpty() || first() !in FORMEL_START) return false
        val rest = drop(1)
        val istZahl = rest.isNotEmpty() &&
            rest.any(Char::isDigit) &&
            rest.all { it.isDigit() || it == ',' || it == '.' }
        return !istZahl
    }
}
