package de.gzgtracker.core

/** Summenzeile fuer die Kopfkarte und die Kontoansicht. */
data class Totals(
    /** Erwartete Erstattung aller offenen Einreichungen (gekauft + eingereicht). */
    val ausstehendCents: Int = 0,

    /** Tatsaechlich erstattetes Geld. */
    val erstattetCents: Int = 0,

    /** Erwartete Erstattung der abgelehnten Einreichungen — verlorenes Geld. */
    val abgelehntCents: Int = 0,

    /** Anzahl aller beruecksichtigten Einreichungen. */
    val anzahl: Int = 0,

    /** Davon offen. */
    val anzahlAusstehend: Int = 0,

    /** Davon erstattet. */
    val anzahlErstattet: Int = 0,
)

object TotalsCalculator {

    /**
     * Was bei dieser Einreichung an Geld zu erwarten ist.
     *
     * Ohne Maximalbetrag in der Aktion gilt der Kaufpreis. Mit Maximalbetrag gilt der
     * kleinere der beiden Werte: Bei "gratis testen" deckt das Maximum den Kaufpreis
     * meist ab, bei einem Teil-Cashback liegt es darunter und begrenzt die Erstattung.
     */
    fun erwarteteErstattungCents(submission: Submission, action: PromoAction?): Int {
        val max = action?.maxRefundCents
        return if (max == null) submission.pricePaidCents else minOf(submission.pricePaidCents, max)
    }

    /**
     * Was bei dieser Einreichung tatsaechlich zaehlt: bei erstatteten Eintraegen der
     * eingetragene Betrag, sonst die Erwartung. Der erstattete Betrag darf vom Kaufpreis
     * abweichen, deshalb hat er Vorrang.
     */
    fun effektiveErstattungCents(submission: Submission, action: PromoAction?): Int =
        if (submission.status == SubmissionStatus.ERSTATTET) {
            submission.refundedAmountCents ?: erwarteteErstattungCents(submission, action)
        } else {
            erwarteteErstattungCents(submission, action)
        }

    /** Rechnet die Summenkarte fuer eine beliebige (bereits gefilterte) Auswahl. */
    fun berechne(
        submissions: List<Submission>,
        actionsById: Map<String, PromoAction>,
    ): Totals {
        var ausstehend = 0
        var erstattet = 0
        var abgelehnt = 0
        var anzahlAusstehend = 0
        var anzahlErstattet = 0

        submissions.forEach { submission ->
            val action = actionsById[submission.actionId]
            val betrag = effektiveErstattungCents(submission, action)
            when (submission.status) {
                SubmissionStatus.GEKAUFT, SubmissionStatus.EINGEREICHT -> {
                    ausstehend += betrag
                    anzahlAusstehend++
                }

                SubmissionStatus.ERSTATTET -> {
                    erstattet += betrag
                    anzahlErstattet++
                }

                SubmissionStatus.ABGELEHNT -> abgelehnt += betrag
            }
        }

        return Totals(
            ausstehendCents = ausstehend,
            erstattetCents = erstattet,
            abgelehntCents = abgelehnt,
            anzahl = submissions.size,
            anzahlAusstehend = anzahlAusstehend,
            anzahlErstattet = anzahlErstattet,
        )
    }

    /** Summen je Konto — beantwortet "was steht auf welchem Konto noch aus?". */
    fun jeKonto(
        submissions: List<Submission>,
        actionsById: Map<String, PromoAction>,
    ): Map<Long, Totals> =
        submissions
            .groupBy { it.accountId }
            .mapValues { (_, eintraege) -> berechne(eintraege, actionsById) }
}
