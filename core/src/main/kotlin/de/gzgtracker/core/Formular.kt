package de.gzgtracker.core

/**
 * Ausfüllhilfe für die Webformulare der Anbieter.
 *
 * Die App trägt ein, was sie ohnehin schon weiß, damit man es nicht ein zweites
 * Mal abtippt. Sie **sendet nichts ab** — das bleibt eine bewusste Handlung, und
 * das Bonfoto muss ohnehin von Hand gewählt werden: Browser lassen Skripte kein
 * Datei-Feld füllen, und das ist auch gut so.
 *
 * Hier stehen die Feldarten und der Bau des Skripts. Das Suchen im Formular
 * passiert im Browser; welche Wörter dabei auf welches Feld zeigen, steht aber
 * hier — an einer Stelle, ohne Android und damit prüfbar.
 */
enum class Formularfeld(
    val schluessel: String,
    val label: String,
    /** Wörter, die im Feldnamen, in der Beschriftung oder im Platzhalter stehen. */
    val muster: List<String>,
) {
    IBAN("iban", "IBAN", listOf("iban", "kontonummer", "bankverbindung")),
    KONTOINHABER(
        "kontoinhaber",
        "Kontoinhaber",
        listOf("kontoinhaber", "account holder", "inhaber"),
    ),
    VORNAME("vorname", "Vorname", listOf("vorname", "firstname", "first_name", "given")),
    NACHNAME("nachname", "Nachname", listOf("nachname", "lastname", "last_name", "surname")),
    EMAIL("email", "E-Mail", listOf("email", "e-mail", "mail")),
    KAUFDATUM(
        "kaufdatum",
        "Kaufdatum",
        listOf("kaufdatum", "einkaufsdatum", "datum", "purchasedate", "bondatum"),
    ),
    BETRAG(
        "betrag",
        "Kaufbetrag",
        listOf("kaufbetrag", "betrag", "preis", "kaufpreis", "summe", "amount", "price"),
    ),
    HAENDLER(
        "haendler",
        "Händler",
        listOf("händler", "haendler", "markt", "geschäft", "geschaeft", "store", "retailer"),
    ),
    PRODUKT(
        "produkt",
        "Produkt",
        listOf("produkt", "artikel", "product"),
    ),
    STRASSE("strasse", "Straße", listOf("straße", "strasse", "street", "adresse")),
    HAUSNUMMER("hausnummer", "Hausnummer", listOf("hausnummer", "hausnr", "nummer")),
    PLZ("plz", "PLZ", listOf("plz", "postleitzahl", "zip", "postcode")),
    ORT("ort", "Ort", listOf("ort", "stadt", "wohnort", "city")),
    TELEFON("telefon", "Telefon", listOf("telefon", "handy", "mobil", "phone", "rufnummer")),
    ;

    companion object {
        fun vonSchluessel(schluessel: String): Formularfeld? =
            entries.firstOrNull { it.schluessel == schluessel }
    }
}

object Formularskript {

    /**
     * Baut das Skript, das die Werte in die Felder der offenen Seite einträgt.
     *
     * Rückgabewert des Skripts ist die Zahl der gefüllten Felder. Die App zeigt
     * sie an — wer nur zwei von sechs getroffen sieht, prüft nach, **bevor** er
     * absendet. Eine stille Teilbefüllung wäre schlimmer als gar keine.
     *
     * Gefüllt wird nur, was leer ist: Hat die Seite ein Feld schon vorbelegt
     * (etwa aus einem Konto beim Anbieter), ist deren Wert der bessere.
     */
    fun baue(werte: Map<Formularfeld, String>): String {
        val eintraege = werte
            .filterValues { it.isNotBlank() }
            .entries
            .joinToString(",\n") { (feld, wert) ->
                val muster = feld.muster.joinToString(",") { "\"${escape(it)}\"" }
                """  {"muster": [$muster], "wert": "${escape(wert)}"}"""
            }

        return """
(function () {
  var vorgaben = [
$eintraege
  ];

  function beschriftung(feld) {
    // Alles zusammentragen, woran ein Feld zu erkennen ist: eigene Attribute,
    // das zugehoerige <label> und der Text des umgebenden Blocks.
    var teile = [feld.name, feld.id, feld.placeholder, feld.getAttribute("aria-label")];
    if (feld.labels) {
      for (var i = 0; i < feld.labels.length; i++) teile.push(feld.labels[i].innerText);
    }
    var huelle = feld.closest("label, .form-group, .field, div");
    if (huelle) teile.push(huelle.innerText);
    return teile.filter(Boolean).join(" ").toLowerCase();
  }

  var felder = document.querySelectorAll(
    "input[type=text], input[type=email], input[type=tel], input[type=number], " +
    "input[type=date], input:not([type]), textarea"
  );

  var gefuellt = 0;
  var vergeben = [];

  for (var f = 0; f < felder.length; f++) {
    var feld = felder[f];
    if (feld.disabled || feld.readOnly || feld.offsetParent === null) continue;
    if (feld.value && feld.value.trim() !== "") continue;

    var text = beschriftung(feld);
    for (var v = 0; v < vorgaben.length; v++) {
      if (vergeben.indexOf(v) !== -1) continue;
      var treffer = false;
      for (var m = 0; m < vorgaben[v].muster.length; m++) {
        if (text.indexOf(vorgaben[v].muster[m]) !== -1) { treffer = true; break; }
      }
      if (!treffer) continue;

      feld.focus();
      feld.value = vorgaben[v].wert;
      // Ohne diese Ereignisse merken Seiten mit React oder Vue nichts von der
      // Aenderung und senden beim Absenden ein leeres Feld.
      feld.dispatchEvent(new Event("input", { bubbles: true }));
      feld.dispatchEvent(new Event("change", { bubbles: true }));
      feld.blur();

      vergeben.push(v);
      gefuellt++;
      break;
    }
  }

  return gefuellt + "/" + vorgaben.length;
})();
        """.trimIndent()
    }

    /**
     * Entschärft einen Wert für den Einbau in das Skript.
     *
     * Die Werte kommen aus Eingabefeldern der App. Ohne diese Behandlung könnte
     * ein Anführungszeichen im Produktnamen das Skript zerreißen — und im
     * schlimmsten Fall eigenen Code einschleusen, der dann auf einer fremden
     * Seite läuft.
     */
    fun escape(wert: String): String = buildString {
        for (zeichen in wert) {
            when (zeichen) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003C") // schliesst kein </script>
                '>' -> append("\\u003E")
                '&' -> append("\\u0026")
                // Diese beiden gelten in JavaScript als Zeilenumbruch und
                // wuerden eine Zeichenkette mittendrin beenden.
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(zeichen)
            }
        }
    }
}


/**
 * Stellt zusammen, was in ein Einreichungsformular gehoert.
 *
 * Bewusst hier und nicht im Bildschirm: Welche Angabe in welches Feld gehoert,
 * ist eine Regel der Sache und keine der Oberflaeche — und so laesst sie sich
 * ohne Android pruefen.
 *
 * Die Reihenfolge der Quellen ist Absicht: Das Konto liefert die Person, die
 * Einreichung den Einkauf. Leere Angaben fallen weg, damit die Trefferzahl
 * ehrlich bleibt.
 */
object Einreichdaten {

    fun aus(
        konto: Account?,
        produktname: String,
        preis: String,
        kaufdatum: String,
        haendler: String?,
    ): Map<Formularfeld, String> {
        val werte = mutableMapOf<Formularfeld, String>()

        konto?.let {
            it.iban?.let { wert -> werte[Formularfeld.IBAN] = wert.filter { z -> !z.isWhitespace() } }
            it.vollerName?.let { wert -> werte[Formularfeld.KONTOINHABER] = wert }
            it.vorname?.let { wert -> werte[Formularfeld.VORNAME] = wert }
            it.nachname?.let { wert -> werte[Formularfeld.NACHNAME] = wert }
            it.email?.let { wert -> werte[Formularfeld.EMAIL] = wert }
            it.strasse?.let { wert -> werte[Formularfeld.STRASSE] = wert }
            it.hausnummer?.let { wert -> werte[Formularfeld.HAUSNUMMER] = wert }
            it.plz?.let { wert -> werte[Formularfeld.PLZ] = wert }
            it.ort?.let { wert -> werte[Formularfeld.ORT] = wert }
            it.telefon?.let { wert -> werte[Formularfeld.TELEFON] = wert }
        }

        werte[Formularfeld.PRODUKT] = produktname
        werte[Formularfeld.BETRAG] = preis
        werte[Formularfeld.KAUFDATUM] = kaufdatum
        haendler?.let { werte[Formularfeld.HAENDLER] = it }

        return werte.filterValues { it.isNotBlank() }
    }
}
