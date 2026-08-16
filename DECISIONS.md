# Entscheidungen

Eine Zeile je Entscheidung: Was, und warum. Das ist die Liste zum Durchgehen —
was dir nicht passt, sag Bescheid, dann drehe ich es um.

## Umgebung und Build

- **Android-Build läuft in GitHub Actions, nicht lokal** — die Egress-Policy dieser
  Session sperrt `dl.google.com`, damit sind Google Maven (AndroidX, AGP) und das
  Android SDK hier nicht ladbar. Maven Central, Gradle-Plugin-Portal, PyPI und npm sind
  offen. Jede Android-Prüfung lief also über CI.
- **Modulschnitt `:core` (reines Kotlin/JVM) und `:app` (Android)** — die Geschäftslogik
  (Beträge, Kontovorschlag, Duplikatsregel, Summen, Filter, CSV) ist damit lokal in
  Sekunden testbar, ohne Android SDK. Nebenbei hält es die Regeln frei von
  Framework-Abhängigkeiten.
- **Kein `plugins { … apply false }` im Root-Build** — ein solcher Block lädt die
  Plugin-Marker aller Module beim Konfigurieren des Root-Projekts, damit bräuchte auch
  `:core` Google Maven. Jedes Modul deklariert seine Plugins selbst.
- **AGP 8.7.3 / Kotlin 2.0.21 / KSP / Gradle 8.11.1** — erprobte Kombination für
  compileSdk 35. KSP statt kapt, weil Room und Hilt es unterstützen und es schneller baut.
- **Version Catalog (`gradle/libs.versions.toml`)** — alle Versionen an einer Stelle.
- **Kein Core-Library-Desugaring** — `java.time` gibt es ab API 26, und minSdk ist 26.
- **Package und Application-ID `de.gzgtracker`** — deutsche App ohne Firmenkontext,
  kein `com.example`.

## Auslieferung

- **Debug-Build mit `applicationIdSuffix ".debug"`** — Debug und Release lassen sich
  parallel auf demselben Gerät installieren.
- **Release fällt ohne Keystore auf den Debug-Key zurück** — so fällt aus CI immer eine
  installierbare APK heraus. Sobald die Secrets gesetzt sind, wird echt signiert. Es
  liegt kein Key im Repo.
- **Zusätzliches Prerelease `debug-latest` mit der Debug-APK** — dein GitHub-Konto hat
  das Artifact-Speicherkontingent erreicht, dadurch schlägt der Artifact-Upload fehl.
  Release-Assets zählen nicht dagegen, also kommst du über die Releases-Seite trotzdem
  an die APK. Der Artifact-Upload bleibt zusätzlich drin, aber als `continue-on-error`.
- **Testberichte werden nur bei Fehlschlag hochgeladen** — spart Artifact-Speicher.
- **Kein `--stacktrace` in den Gradle-Aufrufen** — es schüttet 150 Zeilen Gradle-Interna
  über die eigentlichen Kotlin-Fehlerzeilen.

## Datenmodell

- **Beträge immer `Int` in Cent** — wie von dir vorgegeben. `Money` ist die einzige
  Stelle, die zwischen Cent und deutscher Darstellung übersetzt.
- **`createdAt` als `Instant`, alle fachlichen Daten als `LocalDate`** — sortiert wird
  nach Erfassungszeitpunkt (da zählt die Uhrzeit), angezeigt werden Kalendertage.
- **Einreichungen haben keinen Fremdschlüssel auf Aktionen** — Aktionen kommen und gehen
  mit dem Feed; eine Einreichung muss das Verschwinden ihrer Aktion überleben. Der
  Fremdschlüssel auf Konten bleibt (mit `RESTRICT`), weil Konten nur deaktiviert werden.
- **Enums als Text in der Datenbank, nicht als Ordinalzahl** — sonst verrutschen alle
  gespeicherten Werte, wenn sich die Reihenfolge im Enum ändert. Unbekannte Werte fallen
  auf einen Standard zurück statt zu werfen.
- **Listen (Händler, EANs) mit Trennzeichen `U+001F` in einem Textfeld** — statt einer
  eigenen Tabelle. Sie werden immer komplett gelesen und nie einzeln abgefragt.
- **Konten löschbar nur ohne Einreichungen** — sonst bleibt Deaktivieren, damit die
  Historie stimmig bleibt.

## Kontoregel

- **Eine abgelehnte Einreichung gibt das Konto wieder frei** — es ist kein Geld
  geflossen, ein zweiter Anlauf über dasselbe Konto ist legitim. `ERSTATTET` und
  `EINGEREICHT` belegen das Konto weiter. *Das ist die einzige Auslegung der Kernregel,
  die ich selbst getroffen habe — sag Bescheid, wenn du es strenger willst.*
- **Round-Robin: nie genutzte Konten zuerst, dann das am längsten zurückliegende** —
  bei Gleichstand entscheidet die kleinere Id, damit der Vorschlag reproduzierbar ist.
- **Beim Bearbeiten kollidiert ein Eintrag nicht mit sich selbst** — sonst wäre jede
  Änderung an einer bestehenden Einreichung blockiert.
- **Standard der Regel ist „warnen“, nicht „blockieren“** — beim ersten Start soll die
  App nicht im Weg stehen. Umschaltbar in den Einstellungen.

## Summen

- **Erwartete Erstattung = min(Kaufpreis, Höchstbetrag der Aktion)** — ohne
  Höchstbetrag gilt der Kaufpreis. Bei „gratis testen“ deckt das Maximum den Kaufpreis
  meist ab, bei Teil-Cashback begrenzt es ihn.
- **Bei `ERSTATTET` hat der eingetragene Betrag Vorrang vor der Erwartung** — er darf
  laut Anforderung abweichen, also muss er auch die Summe bestimmen.
- **„Ausstehend“ umfasst `GEKAUFT` und `EINGEREICHT`** — beides ist Geld, das noch
  kommen soll. Abgelehntes wird separat ausgewiesen, aber nur wenn es größer null ist.
- **Die Summenkarte bezieht sich auf die gefilterte Auswahl** — sonst passt die
  Kopfzeile nicht zu dem, was darunter steht.

## Aktions-Feed

- **Feed-URL in den Einstellungen änderbar** — Startwert zeigt auf `main` dieses Repos.
- **Das Repository wird öffentlich gestellt (deine Entscheidung)** — anders kommt die
  App nicht an `actions.json`: `raw.githubusercontent.com` liefert aus privaten Repos
  nichts ohne Zugangsdaten, und ein Token in der APK wäre für jeden auslesbar.
  Öffentlich werden Code, Aktionsdaten und Commit-Adressen; Einreichungen, Bonfotos und
  Konten liegen ausschließlich auf dem Gerät und sind nie im Repo.
- **Das Bauen der APK ist davon unberührt** — Build, Tests, Artifacts und Releases
  funktionieren in privaten Repos genauso. Betroffen war nur der Laufzeit-Abruf des
  Feeds durch die App.
- **Ein leerer Feed löscht nichts** — sonst würde ein kaputter Scraper-Lauf die gesamte
  Aktionsliste auf dem Gerät ausradieren.
- **Aufgeräumt wird pro Quelle** — fällt ein Portal aus, verschwinden nur dessen
  Aktionen aus dem Feed; die anderen behalten ihren Stand. Aktionen mit Einreichungen
  und von Hand angelegte bleiben immer.
- **Aktionen aus dem Feed behalten beim Bearbeiten ihre Quelle** — sonst würde der
  nächste Abgleich sie als verwaist wegräumen.

## Scraper

- **Ein konfigurierbarer Parser über CSS-Selektoren in `sources.yaml` statt Code je
  Portal** — diese Seiten ändern ihr Markup öfter als ihre Struktur. So ist eine kaputte
  Quelle mit einer Zeile YAML repariert, ohne Python und ohne neuen Test. Für Portale
  mit echter Logik gibt es das Parser-Register.
- **Beide Portale aus der Aufgabenstellung sind ersetzt worden** — sie antworten nicht
  mehr: `www.gratis-testen.de` läuft in einen Verbindungs-Timeout,
  `www.aktion-gratis-testen.de` löst nicht einmal im DNS auf. Geprüft vom GitHub-Runner
  aus, also mit freiem Netz, nicht nur aus der gesperrten Entwicklungsumgebung.
  Nachfolger sind `geldzurueck.deals` und `rabattigel.de/cashback` — beide antworten,
  liefern ihre Aktionen im HTML und haben eine klar benannte Kartenstruktur.
- **Selektoren werden am Rohbau abgelesen, nicht an Klassennamen geraten** — ob ein
  Betrag im Text oder in einem Attribut steht und welcher Link zur Aktion führt statt zu
  einem Anker auf derselben Seite, steht nur im Markup. Dafür gibt es
  `inspect_source.py --roh`.
- **Der Umweg über `push` statt `workflow_dispatch`** — GitHub startet
  `workflow_dispatch` und `schedule` nur für Workflow-Dateien auf dem Standardbranch,
  `push` dagegen auf jedem Branch. Nur so kam vor dem Merge überhaupt ein Lauf an die
  echten Seiten.
- **Der tägliche Scrape-Lauf startet trotzdem erst nach dem Merge nach `main`** —
  dieselbe Einschränkung gilt für `schedule`.
- **Ein Feed-Parser für RSS und Atom neben dem CSS-Parser** — ein Feed ist eine Zusage
  des Betreibers, maschinenlesbar zu bleiben, überlebt jede Seitenumgestaltung und
  kostet weniger Last. Bei den geprüften Portalen taugte keiner: `geldzurueck.deals`
  antwortet mit 404, die WordPress-Seiten liefern unter `/feed/` die *Kommentare* statt
  der Aktionen. Der Parser bleibt trotzdem im Register, damit die nächste Quelle mit
  echtem Feed ohne Code auskommt.
- **Stabile Id aus Titel + Marke + Einsendeschluss** — bewusst *ohne* URL (Portale
  hängen Tracking-Parameter an) und *ohne* Betrag (wird nachträglich korrigiert), sonst
  bekäme dieselbe Aktion ständig eine neue Id.
- **Umlaute werden vor der Unicode-Zerlegung umgeschrieben** — sonst wird aus „ü“ ein
  „u“ und „Müller“ träfe sich nie mit „Mueller“.
- **EANs werden gegen die Prüfziffer validiert** — sonst landen Artikelnummern und
  Telefonnummern aus dem Fließtext als EAN in der App, und der Scan trifft die falsche
  Aktion.
- **Händler nur gegen eine feste Namensliste** — ein aus dem Fließtext geratener „Markt“
  wäre als Filter wertlos.
- **Ohne Währungszeichen gilt eine Zahl nur dann als Betrag, wenn rechts weder Ziffer
  noch Einheit steht** — auf den echten Seiten sahen „30.08.2026“, „1.450 Einlösungen“
  und „0,75 l“ alle wie Geldbeträge aus, und der Scraper hat zwei davon auch so
  eingetragen. Aufgefallen ist das erst beim Lesen des Ergebnisses, nicht beim Zählen.
- **Der Sammellauf schreibt sein Ergebnis lesbar ins Log** — „19 Aktionen gefunden“
  beweist nichts. Erst Titel, Betrag und Frist nebeneinander zeigen, ob die Selektoren
  Sinn ergeben.
- **Wiederkehrende Zusätze werden aus dem Titel geschnitten (`titel_entfernen`)** —
  rabattigel hängt an jeden Titel „[gratis testen, Geld zurück!]“. Das steht schon im
  Feld `type`; im Titel verdeckte es neunzehnmal den Produktnamen. Bleibt nach dem
  Kürzen nichts übrig, gilt der ursprüngliche Titel — lieber laut als namenlos.
- **Dieselbe Aktion darf aus zwei Portalen doppelt auftauchen** — die Titel weichen
  voneinander ab („Bonduelle Frische Salate“ gegen „Bonduelle Frische Salate Gratis
  Testen mit Scondoo“), und eine Ähnlichkeitsregel würde mal richtig, mal falsch
  zusammenfassen. Ein doppelter Eintrag ist ärgerlich, ein fälschlich verschluckter
  wäre schlimmer.
- **Der Countdown „31 Tage“ wird nicht in ein Datum umgerechnet** — die Aktions-Id
  enthält den Einsendeschluss. Ein täglich um einen Tag wanderndes Datum ergäbe jeden
  Morgen eine neue Id, und die App führte dieselbe Aktion immer wieder als neu.
- **Ausgefallene Quellen färben den Job nicht rot** — sonst rauscht jede Portalwartung
  als Fehlalarm durch. Rot wird er erst, wenn keine einzige Quelle mehr liefert.
- **`generated_at` ändert sich nur bei echter Änderung** — sonst gäbe es jeden Tag einen
  Commit, der nichts als den Zeitstempel dreht.
- **Dieselbe Aktion aus zwei Portalen wird nur bei identischer Einreichungsadresse
  zusammengefasst** — wer auf demselben Formular einreicht, macht bei derselben Aktion
  mit. Titel zu vergleichen wäre verlockend („Bonduelle Frische Salate" gegen „Bonduelle
  Salat Gratis Testen via scondoo"), würde aber mal richtig und mal falsch zusammenwerfen.
  Eine fälschlich verschluckte Aktion ist schlimmer als eine doppelt angezeigte.
- **Beim Zusammenfassen bleibt die Quelle die des Grundeintrags** — die App räumt je Quelle
  auf; ein zusammengesetzter Wert wie „a+b" würde dabei nie wieder getroffen und der
  Eintrag bliebe ewig stehen.
- **Ehrlicher User-Agent mit Projektlink statt getarntem Browser** — fair gegenüber den
  Betreibern und macht Probleme nachvollziehbar.
- **Der Feed sammelt nur volle Erstattungen** (`nur_arten: [gratis_testen]`) — Teilbeträge
  sind nicht der Zweck dieser App. Dafür musste die Arterkennung lernen, dass
  „100 % Cashback" gratis testen *ist*: Das Wort Cashback allein sagt nichts über die
  Höhe, und ohne diese Unterscheidung hätte der Filter genau die Volltreffer weggeworfen.
- **Abgelaufene Aktionen fliegen raus, Aktionen ohne Frist bleiben** — Portale lassen alte
  Einträge stehen. Eine abgelaufene Aktion ist schlimmer als eine fehlende: Man kauft das
  Produkt und erfährt erst beim Einreichen, dass nichts mehr geht. „Keine Frist bekannt"
  ist aber etwas anderes als „abgelaufen", und bei einer Quelle fehlt die Frist immer.
- **`submit_url` neben `url`** — `url` zeigt auf den Artikel im Portal, `submit_url` auf
  das Formular des Anbieters. Nur so führt ein Fingertipp dorthin, wo man tatsächlich
  einreicht. Wo es keine gibt, sagt die Beschriftung das ehrlich („Aktionsseite öffnen"
  statt „Zur Einreichung").
- **Detailseiten werden nur dort nachgeladen, wo die Übersicht den Link nicht hergibt** —
  ein zusätzlicher Abruf je Aktion. rabattigel verlinkt schon in der Liste,
  geldzurueck.deals erst auf der Detailseite; mydealz baut seine Links per JavaScript,
  da bringt Nachladen nichts.
- **`requirements` wird konservativ erkannt, im Zweifel leer** — die Checkliste sagt dann
  „steht nicht im Feed" statt einen Haken zu erfinden. Ein erfundener Haken schickt
  jemanden mit dem falschen Foto los, und die Erstattung fällt aus; ein fehlender kostet
  einen Blick auf die Aktionsseite.
- **Was mydealz als `pepper:merchant` liefert, wird einsortiert statt ins Markenfeld
  geschrieben** — mal ist es die Marke („Milka"), mal der Händler („ROSSMANN"), mal die
  Einreichplattform („scondoo"). Sonst hieße die Hälfte aller Aktionen „scondoo".

## Erstanbieter-Quellen

- **Kampagnen werden beim Urheber gelesen, nicht beim Portal** — ein Portal ist eine
  redaktionell gepflegte Sammlung, und wer täglich deren Bestand abzieht, entnimmt einen
  wesentlichen Teil einer fremden Datenbank (§ 87b UrhG). Von je einem Erstanbieter je
  eine Kampagne zu nehmen ist etwas anderes: Die Aktionsseite des Herstellers hat den
  einzigen Daseinszweck, gefunden zu werden. Nebeneffekt: Kein Portalumbau kann diese
  Quellenart brechen, weil sie kein Portal anfasst.
- **Entdeckung über Certificate-Transparency-Logs** — Aktionsplattformen legen je Kampagne
  eine Subdomain an (belegt: `airwick.justsnap.eu`), jede Subdomain braucht ein Zertifikat,
  jedes Zertifikat steht nach RFC 6962 in einem öffentlichen Protokoll. Eine Abfrage bei
  crt.sh am Tag liefert damit jede neue Kampagne, rein passiv und ohne Anfrage an die
  Zielsysteme. Sitemaps daneben, weil pfadbasierte Plattformen so nicht auffindbar sind.
- **Was das Zertifikat findet, ist noch keine laufende Aktion** — ein Zertifikat existiert
  regelmäßig Wochen vor dem Kampagnenstart. So eine Aktion zu veröffentlichen verrät die
  Marketingplanung des Herstellers, den *niemand* um eine Ankündigung gebeten hat. Deshalb
  die Regel `gestartet` in `pruefung.py`.
- **Für Portale gilt das ausdrücklich nicht** — mydealz kündigt Aktionen bewusst an, oft
  mit dem Startdatum im Titel („ab dem 17.08."). Da gibt es nichts zu verraten, und die
  Merkliste lebt genau davon. Die Regel greift deshalb nur bei entdeckten Quellen
  (`Kontext.nur_gestartete`). Beim ersten Lauf mit der Prüfschicht fielen sonst zwei echte
  Aktionen aus dem Feed, nur weil sie zwei Tage später starteten.
- **Eine künftige Aktion zeigt ihren Beginn, nicht ihre Frist** — sie in den Feed zu lassen
  genügt nicht: In der Liste stand bisher nur „Einsendeschluss 30.09.", und damit sieht
  eine Aktion, die erst am 17.08. beginnt, aus wie eine laufende. Wer daraufhin kauft, hat
  einen Kassenbon von heute — der liegt vor dem Aktionszeitraum, und die Erstattung fällt
  aus. `PromoAction.tageBisStart` in `core` entscheidet das, die Liste schreibt „Startet in
  2 Tagen", die Detailansicht „Startet erst am". Das ist dieselbe Haltung wie beim
  Ablauf-Filter, nur am anderen Ende.
- **JSON-LD vor Modell** — was eine Seite ohnehin für Suchmaschinen veröffentlicht, ist
  exakt, kostenlos und vom Anbieter gepflegt. Das Modell kommt nur dran, wo das nichts
  hergibt; bei einer Plattform mit sauberem Markup läuft ein ganzer Lauf ohne einen
  einzigen Modellaufruf.
- **Der Preis aus JSON-LD wird nicht übernommen** — `offers.price` ist der Verkaufspreis
  des Produkts, nicht die Erstattung. Bei „gratis testen" ist beides oft dasselbe, aber
  eben nur oft: Sobald die Aktion bei „bis zu 4,99 €" deckelt, wären wir mit dem Ladenpreis
  daneben, und zwar nach oben.
- **Das Modell zitiert, unsere Parser rechnen** — es liefert Betrag und Frist als
  wörtliches Zitat von der Seite („4,99 €"), nie als fertige Zahl. Die Umrechnung machen
  `betrag_in_cent` und `datum_iso`, die seit jeher wissen, dass „0,75 l" kein Geldbetrag
  ist und ein Datum kein Preis. Ein Modell erfindet einen Betrag lieber, als keinen zu
  nennen, und der erfundene sieht genauso plausibel aus wie der echte.
- **Der Betrag muss wörtlich im Seitentext stehen** — die wichtigste Regel der Prüfschicht.
  Sie kostet gelegentlich einen korrekten Betrag, der nur als Bild vorliegt, und verhindert
  dafür, dass jemand ein Produkt kauft, weil bei uns eine Zahl stand, die es nirgends gab.
- **Der Einreichungslink muss zur Aktionsseite gehören** — `submit_url` stammt aus dem
  Seitentext, und den schreibt der Betreiber der Seite, nicht wir. Auf genau diesen Link
  führt die App zum Einreichen, und dort füllt sie auf Knopfdruck IBAN, Bankverbindung
  und Anschrift in die Formularfelder. Ein untergeschobenes Ziel bekäme das geschenkt.
  Deshalb die Regel `einreichung_am_ort` bei entdeckten Quellen: gleicher Host oder
  Unterdomäne. Für Portale gilt sie nicht — dort *ist* der Wechsel vom Artikel zum
  Herstellerformular der Zweck.
- **Nutzungsvorbehalte werden auch in Prosa gesucht** — § 44b UrhG erlaubt automatisiertes
  Auswerten nur, solange kein maschinenlesbarer Vorbehalt erklärt ist, und das LG Hamburg
  hat entschieden, dass dafür auch natürliche Sprache genügt. `robots.txt` allein zu prüfen
  reicht seitdem nicht. Die Erkennung ist absichtlich schief eingestellt: Ein falscher
  Alarm kostet eine Quelle, ein übersehener Vorbehalt die Rechtsgrundlage.
- **Die Prüfschicht überspringt Regeln ohne Grundlage, statt zu scheitern** — die
  gewachsenen Portalquellen haben keinen Seitentext, also läuft dort die Betragsprüfung
  nicht. Eine Regel ohne Grundlage darf nicht raten, sonst verlören die Portalquellen
  schlagartig alles.
- **Jede Ablehnung steht mit Begründung im Log** — eine still verschwundene Aktion ist von
  einer nie gefundenen nicht zu unterscheiden. Wer im Actions-Lauf nachsieht, soll lesen
  können, *warum* eine Aktion fehlt.
- **Vorgabe ist `claude-opus-5` bei Denktiefe `low`, beides umstellbar** — die Denktiefe,
  weil Abschreiben aus vorliegendem Text keine Denkaufgabe ist. Das Modell bleibt bei der
  Vorgabe für neue Anbindungen; ob ein kleineres reicht, ist eine Kostenentscheidung des
  Betreibers und gehört nicht in den Code. `GZG_MODELL`, `GZG_EFFORT` und `--ohne-modell`
  regeln das ohne Codeänderung.
- **Ohne API-Schlüssel läuft alles weiter** — der Lauf meldet das einmal und wertet nur
  JSON-LD aus. Sonst wäre die CI von einem Secret abhängig, das in keinem Fork existiert.
- **Bekannte Kampagnen werden nicht jeden Tag neu gelesen** — der Kostenhebel. Ohne die
  Wiederverwendung zahlt jeder Lauf das Volle: Eine gestern gefundene Kampagne bekäme
  heute wieder einen Abruf und einen Modellaufruf, obwohl sie unverändert in
  `actions.json` steht. Die Nutzerzahl spielt dabei nie eine Rolle — die App lädt nur die
  fertige Datei, das Modell läuft einmal am Tag in Actions.
- **Welcher Wochentag eine Kampagne aufgefrischt wird, entscheidet ihre Adresse** — ein
  Zeitstempel je Aktion wäre der naheliegende Weg gewesen, hätte aber ein neues Feld in
  `actions.json` gebraucht, und dieses Format liest die App. Ein Hash der Adresse modulo
  `auffrischen_tage` ist stabil (dieselbe Kampagne trifft immer denselben Tag),
  gleichverteilt (die Last fällt nicht an einem Tag an) und braucht kein Schema.
- **Ohne Frist wird immer neu gelesen** — die einzige Stelle, an der „keine Frist bekannt"
  streng ausgelegt wird. Sonst lässt sich nicht sagen, ob die Kampagne noch läuft, und
  eine abgelaufene weiterzuschleppen wäre schlimmer als ein Abruf zu viel.
- **`--only` startet auch eine abgeschaltete Quelle** — die README schreibt für jede neue
  Quelle einen Probelauf vor, bevor sie scharf geschaltet wird. Vorher filterte `--only`
  erst *nach* dem Aktiv-Filter, und der empfohlene Befehl endete bei „Keine aktive Quelle".
  Wer eine Quelle ausdrücklich benennt, meint sie auch.

## Design

- **Schriften liegen als Latin-Subset im APK statt als Downloadable Font** — rund
  390 KB. Downloadable Fonts brauchen Play Services und ein Zertifikats-Resource; bis
  die Schrift da ist, blitzt die Systemschrift auf. Eingebettet rendert die App offline
  und beim ersten Frame korrekt. Lizenzen (SIL OFL 1.1) liegen unter `licenses/`.
- **Kontofarben erscheinen nur als kleiner Punkt in Konto-Zusammenhängen** — in der
  Belegliste steht das Konto als Text. Zwei Farbsysteme in derselben Zeile würden um
  Aufmerksamkeit konkurrieren, und Farbe gehört dem Status.
- **Im Dunkelmodus wird nur Rot minimal aufgehellt** (`#B3261E` → `#CF4339`) — damit die
  Badge-Fläche gegen das dunkle Papier die 3:1-Grenze für Nicht-Text hält. Gelb und Grün
  halten sie unverändert.
- **Die Kontowarnung nutzt Rot nicht als Fläche** — Rot ist im Belegstapel der
  abgelehnte Status. Die Warnung trägt ihre Bedeutung über Symbol und Text.
- **Eigenes Layout für die Belegzeile statt `Row` mit `weight`** — ein gewichtetes Kind
  mit `fill = false` gibt übrige Breite nicht weiter, zwischen Bezeichnung und Punkten
  klaffte sonst eine Lücke. Bezeichnung und Betrag sitzen auf einer gemeinsamen
  Grundlinie, die Punkte knapp darüber.
- **Die Zahnbreite der Abrisskante wird auf die Kartenbreite nachjustiert** — sonst
  hängt rechts ein angeschnittener Zahn und es wirkt wie ein Rendering-Fehler.
- **Der Stempel sitzt bei −2,5°** — innerhalb der gewünschten 2–3°, gegen den
  Uhrzeigersinn, damit er nicht mit dem rechtsbündigen Betrag kollidiert.
- **Tap öffnet die Detailansicht, Wischen stuft weiter** — nach rechts eine Stufe
  vorwärts, nach links abgelehnt. Der Eintrag verschwindet dabei nie, die Zeile federt
  zurück. Den Status direkt setzen geht in der Detailansicht.
- **Beim Umstellen auf `ERSTATTET` fragt ein Dialog nach Betrag und Datum** — vorbelegt
  mit der Erwartung, meist ist es ein Tipp.

## Erfassen und Scannen

- **Nur EAN-13 und EAN-8 im Scanner** — weniger Formate heißt schnellere Erkennung und
  keine Fehltreffer auf QR-Codes, die auf Verpackungen herumstehen.
- **Ein Code wird erst nach zwei aufeinanderfolgenden Erkennungen übernommen** —
  einzelne Fehllesungen bei unscharfem Bild kommen vor, ein falscher Treffer wäre
  teurer als ein Sekundenbruchteil Wartezeit.
- **Mehrere Treffer zu einer EAN: der Nutzer wählt** — raten wäre hier falsch.
- **Bonfotos werden auf 2000 px lange Kante und JPEG 85 heruntergerechnet** — ein Bon
  bleibt bequem lesbar, aber ein Jahr Sammeln füllt nicht den Gerätespeicher. Die
  EXIF-Drehung wird dabei angewandt, sonst kommen hochkant fotografierte Bons liegend an.
- **Der alte Bon wird erst nach erfolgreicher Übernahme gelöscht** — sonst wäre bei
  einem Fehlschlag der alte weg und der neue nicht da.
- **Bonfotos liegen app-intern, nicht im MediaStore** — die Belege gehen niemanden außer
  der App etwas an, und so braucht sie keine Speicherberechtigung.

## Export

- **CSV mit Semikolon und BOM** — so öffnet Excel in deutscher Spracheinstellung die
  Datei ohne Importdialog und mit korrekten Umlauten.
- **Felder mit führendem `=`, `+`, `-` oder `@` werden entschärft** — Tabellenprogramme
  würden solche Zellen als Formel ausführen. Ausgenommen sind erkennbare Zahlen, damit
  „-3,99“ ein Betrag bleibt und kein Text wird.
- **Der Export liegt im Cache** — er ist eine Momentaufnahme zum Weitergeben, kein
  Dokument, das die App verwalten müsste.

## Merkliste

- **Eigene Tabelle statt einer Spalte an der Aktion** — Aktionen werden bei jedem
  Feed-Abgleich ersetzt und verschwundene weggeräumt. Eine Merkung ist eine Entscheidung
  des Nutzers, keine Feed-Angabe, und muss das überleben.
- **Eine Merkung schützt die Aktion vorm Aufräumen** — sonst wäre der Einkaufszettel
  morgens im Supermarkt plötzlich halb leer, weil ein Portal einen Eintrag kurz nicht
  ausgeliefert hat.
- **Das Häkchen „im Wagen" steht in der Datenbank, nicht im Bildschirmzustand** — im Laden
  verlässt man die App zwischendurch und will danach nicht von vorn anfangen.
- **Wer eine gemerkte Aktion erfasst, nimmt sie vom Zettel** — sonst hakt man dieselbe
  Zeile zweimal ab, einmal im Laden und einmal in der App.

## Bon auswerten

- **Texterkennung läuft auf dem Gerät, das Modell ist einkompiliert** — kein Netz, kein
  Dienst. Auf einem Kassenbon steht, was jemand wann wo gekauft hat; das verlässt das
  Telefon nicht.
- **Die Auswertung liegt in `:core`, nicht in der App** — so ist sie ohne Android und ohne
  Kamera testbar, und genau sie ist der schwierige Teil.
- **Ohne Schlüsselwort gibt es keinen Vorschlag** — „größter Betrag auf dem Bon" wäre
  verlockend, trifft aber bei Barzahlung den gegebenen Schein. Lieber kein Vorschlag als
  ein falscher, den man übersieht: Er fällt sonst erst auf, wenn die Erstattung ausbleibt.
- **„Bar", „Rückgeld", „MwSt" und Verwandte werden ausgeschlossen** — sie tragen Beträge,
  aber nie den bezahlten Preis.
- **Beim Datum gewinnt das jüngste, das nicht in der Zukunft und nicht älter als ein Jahr
  ist** — auf einem Bon stehen auch Haltbarkeits- und Gutscheindaten.
- **Vorgefüllte Werte tragen den Hinweis „Aus dem Bon — prüfen" unter dem Feld**, nicht als
  Meldung, die wieder verschwindet. Sobald man den Wert anfasst, verschwindet der Hinweis.
- **Vorhandene Eingaben werden nie überschrieben** — wer den Preis korrigiert und danach
  ein besseres Foto macht, behält seine Korrektur.
- **Nur Bilder mit Bon werden durchsucht** — ein Produktfoto enthält keinen Betrag.

## App

- **„Beleg eintragen" ist die Hauptaktion, nicht „Produkt scannen"** — der tägliche Weg
  ist: Aktion aussuchen, kaufen, fotografieren, eintragen. Der Barcode-Scan hilft nur im
  Sonderfall „steht im Laden vor einem Produkt und will wissen, ob dazu etwas läuft"; er
  sitzt deshalb in der Titelzeile statt auf dem großen Knopf.
- **Drei feste Belegplätze statt einer freien Anhangsliste** — die Portale verlangen genau
  drei Dinge: das Produkt allein, den Bon allein, oder beides zusammen auf einem Bild.
  Eine generische Liste hätte die Frage offengelassen, die beim Einreichen zählt: *Was
  zeigt das Bild?*
- **Verlangte Belege werden hervorgehoben, nicht erzwungen** — die Checkliste aus dem Feed
  ist nicht immer vollständig, und wer ein Bild zu viel macht, verliert nichts.

- **Echte Room-Migrationen statt `fallbackToDestructiveMigration()`** — in dieser Datenbank
  stehen Einreichungen, Konten und die Pfade zu den Bonfotos. Ein Update, das die Belege
  eines halben Jahres wegwirft, wäre der schlimmste denkbare Fehler dieser App.
- **`url` und `submit_url` müssen `http(s)` sein** — beide kommen aus fremden Portalen. Ein
  `javascript:`- oder `intent:`-Link daraus würde beim Antippen in einer anderen App
  landen; das gehört gar nicht erst in die Datenbank.

- **Aus dem Bon zählt der Posten des Aktionsprodukts, nicht die Summe** — erstattet wird das
  eine Produkt. Wer den Gesamtbetrag eines Wocheneinkaufs einreicht, bekommt nichts oder
  fällt unangenehm auf. Gesucht wird deshalb die Zeile mit den meisten Wortübereinstimmungen
  zum Produktnamen; Bons kürzen ab ("BIFI TASTY B."), also zählt schon ein Wortanfang.
  Erst wenn keine Zeile passt, gilt wieder die ausgewiesene Summe, danach der größte Betrag.
- **Der Händler kommt aus den ersten acht Zeilen** — weiter unten stehen Werbetexte
  ("Auch erhältlich bei Rossmann") und Adressen, in denen ein Marktname zufällig vorkommt.
  Der Abgleich läuft über Wortgrenzen, sonst fände "dm" sich in "Admiralstraße" wieder.
- **Der eingebaute Browser meldet sich ohne „wv"** — die Standard-Kennung einer
  WebView enthält dieses Kürzel, und einige Anbieter liefern darauf eine leere Seite aus.
  Dazu Ladebalken, sichtbare Fehlermeldung und „Im Browser öffnen": Vor einer weißen Fläche
  zu stehen, ohne zu wissen warum, ist der schlechteste Ausgang.

- **Das Datei-Feld der Anbieterseite bedient die App mit den eigenen Fotos** — ein
  eingebauter Browser öffnet ohne `onShowFileChooser` gar keinen Dateidialog; das Antippen
  von „Datei auswählen" blieb wirkungslos. Genau dieses Foto ist aber der Kern jeder
  Einreichung. Angeboten werden zuerst die Bilder dieser Einreichung, dann die Galerie.
  Eingefügt wird nur, was von Hand ausgewählt wurde — ein Skript darf ein Datei-Feld
  nicht füllen, und das soll auch so bleiben.
- **Weiterleitungen werden beim Sammeln aufgelöst, nicht im Gerät** — mydealz verlinkt über
  `/visit/threadmain/<id>`. In der App erschien deshalb erst ein fremdes Logo, und wenn die
  Zwischenseite hakte, gar nichts. Der Scraper ruft die Seite ohnehin ab und merkt sich, wo
  er landet. Bleibt die Weiterleitung auf demselben Host, bleibt die kürzere Adresse stehen.
- **„Speichern und einreichen" setzt den Status auf eingereicht** — sonst steht der Eintrag
  danach als „gekauft" in der Liste, obwohl man gerade eingereicht hat. Umgekehrt führt ein
  abgebrochener Vorgang nicht in eine Sackgasse: In der Detailansicht steht „Nochmal
  einreichen", und der Status lässt sich mit einem Tipp zurücksetzen.

- **Bonzeilen werden aus der Lage auf dem Bild zusammengesetzt** — die Texterkennung liefert
  Blöcke, keine Zeilen. Auf einem Kassenbon heisst das regelmäßig: erst alle Artikelnamen
  untereinander, dann alle Beträge. Im Text steht dann `BOROTALCO DEO` zwanzig Zeilen über
  `3,45`, und keine Auswertung kann daraus noch ablesen, was zusammengehört. Genau daran
  scheiterten Produktpreis, Händler und Kaufdatum am Gerät. Jetzt gilt: Was auf gleicher
  Höhe steht, stand auf dem Papier in einer Zeile.
- **Aus dem Bon Gelesenes darf ein zweites Foto überschreiben** — von Hand Eingetragenes
  nicht. Wer wegen eines falschen Vorschlags noch einmal fotografiert, will den neuen Wert
  sehen; wer selbst korrigiert hat, will seine Korrektur behalten.
- **Anrede und Geburtsdatum stehen am Konto** — beides verlangen die Formulare regelmäßig.
  Für die Anrede füllt das Skript jetzt auch Auswahlfelder: Dort steht kein freier Text,
  sondern eine Liste, und „Herr" muss auf den passenden Eintrag gelegt werden.
- **Die Leiste unter dem Formular ist eine Zeile** — jede Zeile dort fehlt der Seite darüber,
  und ein Anbieterformular, von dem man vier Felder sieht, ist mühsam.
- **Eine leere Seite meldet sich selbst** — bleibt nach dem Laden kein Text übrig, sagt die
  App das und bietet den Browser an, statt eine weisse Fläche stehenzulassen.

- **Lange Bons werden in überlappenden Streifen gelesen** — die Texterkennung arbeitet mit
  einer festen inneren Größe. Ein ganzer Kassenbon auf einem Bild heißt deshalb wenige
  Bildpunkte je Zeile, und dann liest sie nur noch aus nächster Nähe etwas. Nur lässt sich
  ein Bon, der vollständig zu sehen sein muss, nicht aus nächster Nähe fotografieren. Jeder
  Streifen bekommt ein Vielfaches an Bildpunkten je Zeile; die Rahmen werden zurückgerechnet,
  und was doppelt aus dem Überlappungsbereich kommt, fliegt raus — sonst würde aus zwei mal
  `3,45` ein zweiter Posten. Dazu 3200 statt 2000 Bildpunkte beim Speichern.
- **Die Suche ist eine Lupe** — die Suchleiste stand dauerhaft im Bild und nahm ein Sechstel
  des Bildschirms, obwohl man selten sucht.
- **Sortiert wird nach Frist, Betrag oder Name** — die Frist bleibt der Standard: Was morgen
  abläuft, muss oben stehen.
- **Produktbilder werden vollständig gezeigt, nicht zugeschnitten** — bei „Crop" fehlte
  regelmäßig die halbe Packung, und im Laden erkennt man sie dann nicht wieder.
- **Eigene Aktionen lassen sich beim Erfassen anlegen** — der Feed kennt nicht alles. Manches
  steht nur auf der Packung, im Prospekt oder auf einem Aufsteller. Ohne diesen Weg ließe sich
  so ein Kauf überhaupt nicht erfassen, und genau dafür ist die App da.
- **Die Feed-Adresse steht nicht mehr in den Einstellungen** — sie ist eine Einstellung der
  App, keine des Nutzers. Wer sie versehentlich ändert, sieht keine Aktionen mehr und weiß
  nicht, warum.

## Farbe und Hierarchie

- **Farbe hat drei Rollen: Status, Interaktion, Struktur** — vorher galt „Farbe nur für
  Status", alles Bedienbare war `ink`. Das Ergebnis war eine Oberfläche, auf der ein Knopf
  aussah wie eine Überschrift und die Statusfarben zwar auffielen, aber außerhalb des
  Stempels nichts trugen. Der Akzent ist ein tiefes Tintenblau — die einzige kräftige
  Farbe, die Gelb, Grün und Rot nicht ins Gehege kommt, auch nicht bei Rot-Grün-Schwäche.
- **Rot markiert ablaufende Fristen — als Textfarbe, nie als Fläche** — Flächen bleiben dem
  Status vorbehalten, sonst sähe eine Aktion, die morgen endet, aus wie eine abgelehnte
  Einreichung. „Einsendeschluss morgen" sah vorher aus wie „in acht Tagen", und das ist die
  kritischste Angabe der ganzen Liste.
- **Der Statusstreifen macht die Statusfarbe erst nützlich** — beim Überfliegen einer langen
  Liste liest niemand jeden Stempel. Eine durchgehende Farbkante am linken Rand sieht man,
  ohne hinzusehen. Gezeichnet statt gelegt: In einer Liste steht die Zeilenhöhe erst beim
  Zeichnen fest, ein Element mit „voller Höhe" hätte darin keine bekommen.
- **Pro Bildschirm genau eine primäre Handlung** — zwei gefüllte Knöpfe untereinander heben
  sich gegenseitig auf, dann führt keiner mehr.
- **Der Belegplatz ist ein Ablagefeld, kein Knopfpaar** — vorher standen dort zwei 72 dp
  hohe Kästen nebeneinander, in denen „Fotografieren" mitten im Wort umbrach; eine
  Nebenhandlung sah aus wie die Hauptsache der Seite. Gestrichelt heißt überall „hier
  gehört etwas hin", die Galerie steht leise daneben.

- **Kontingente werden aus den Teilnahmebedingungen gelesen** — viele Aktionen sind
  gedeckelt („1.000 Teilnahmen pro Woche") und werden zu einem festen Zeitpunkt
  zurückgesetzt. Wer das nicht weiß, kauft das Produkt und merkt beim Einreichen, dass er
  zu spät dran war. Gelesen werden Zahl, Zeitraum und Zurücksetzung; einen *Live-Zähler*
  gibt es nicht, weil die Seiten ihn nicht hergeben.
- **„Sobald das Kontingent erschöpft ist …" heißt nicht, dass es erschöpft ist** — dieser
  Satz steht in fast jeden Teilnahmebedingungen. Als „erschöpft" zählt nur eine Aussage im
  Präsens ohne Bedingungswort in den 70 Zeichen davor; sonst wären alle Aktionen dauerhaft
  als tot markiert. Geprüft wird der unmittelbare Zusammenhang, nicht der Satz: Ein aus
  HTML gewonnener Seitentext hat keine verlässlichen Satzgrenzen.
- **Eine Zahl wird erst zur Obergrenze, wenn ein Wort sie dazu macht** — der erste Anlauf
  las den Teilnehmerzähler einer Seite („schon 30.652 Teilnahmen!") als Kontingent. Jetzt
  muss in der Nähe „begrenzt", „maximal", „insgesamt", „Kontingent" oder ein Zeitraum
  stehen. Am echten Feed geprüft, nicht nur an erfundenen Beispielen.
- **Erinnerungen mit dem AlarmManager, ungenau gestellt** — für eine Meldung zu einem
  Zeitpunkt braucht es keine Bibliothek, und ein ungenauer Alarm erspart die
  Sonderberechtigung für exakte Wecker. Gestellte Erinnerungen liegen in der Datenbank,
  weil Wecker keinen Neustart überleben.
- **Ungenau ja, aber doze-fest** — `set()` klang nach „höchstens eine Stunde später“, so
  stand es hier auch. Im Doze-Modus hält Android einen solchen Wecker aber bis zum
  nächsten Wartungsfenster zurück, und über Nacht sind das Stunden. Am Tag des
  Einsendeschlusses ist eine Erinnerung, die erst nachmittags ankommt, wertlos. Jetzt
  `setWindowAndAllowWhileIdle` mit einer halben Stunde Fenster: weckt aus Doze heraus,
  lässt dem System weiter Spielraum zum Bündeln und braucht trotzdem keine
  Sonderberechtigung.
- **Wecker werden von einem Empfänger neu gestellt, nicht erst beim App-Start** — die
  Zeile darüber stand schon länger so da, gerufen hat `stelleErinnerungenNeu()` aber
  niemand. Eine Erinnerung fiel damit beim nächsten Neustart still aus, und gemerkt hat
  man es erst nach der Frist. Jetzt hängt ein Empfänger an `BOOT_COMPLETED` und
  `MY_PACKAGE_REPLACED` — ein App-Update räumt die Wecker genauso ab. Der Aufruf beim
  App-Start bleibt zusätzlich stehen, für die Fälle, in denen der Empfänger übergangen
  wird; erneutes Stellen ersetzt den Wecker nur.
- **Der eingebettete Browser zeigt seinen Gastgeber, und bei fremder Domain fragt die App
  nach** — „Daten einfügen" schreibt IBAN, Bankverbindung, Geburtsdatum und Anschrift in
  die Felder der *gerade geladenen* Seite, und der Browser folgt jeder Weiterleitung.
  Ohne Angabe sieht eine fremde Domain aus wie die Aktionsseite selbst. Unterdomänen
  gelten als dieselbe Herkunft (`airwick.justsnap.eu` und `justsnap.eu` sind eine
  Kampagne), eine nackte Endung nicht — sonst wäre jeder `.de`-Host mit jedem anderen
  verwandt. Ohne geladene Seite wird **nicht** gewarnt: Eine Warnung ohne Grundlage lehrt
  nur, sie wegzuklicken.
- **Der Barcode-Scanner ist entfallen** — mit ihm CameraX und die Barcode-Bibliothek. Er
  löste einen Sonderfall („steht im Laden vor einem Produkt"), den der tägliche Weg nicht
  braucht, und kostete zwei Bibliotheken.

## Sonstiges

- **Backup nur noch für die Einstellungen, Datenbank und Belege bleiben auf dem Gerät** —
  die frühere Regel nahm alles mit, auch in die Cloud-Sicherung. In der Datenbank stehen
  aber IBAN, BIC, Geburtsdatum, Anschrift, Telefon und E-Mail; in `receipts/` liegen die
  Bonfotos. Das ist genau das, was der erste Satz der README ausschließt. Ab Android 12
  trennt `data-extraction-rules` beides: `cloud-backup` bekommt nur die
  Einstellungsdatei, `device-transfer` weiterhin alles — ein Geräteswechsel kostet dort
  also keine Einreichung. Auf Android 8 bis 11 gibt es diese Trennung nicht, dort steht
  nur die Einstellungsdatei drin; der Preis ist ein Geräteswechsel ohne Einreichungen,
  und das ist die günstigere Seite der Abwägung.
- **Deutsche Bezeichner in der App-eigenen Fachlogik, englische in Framework-Nähe** —
  `pruefeKonto`, `belegteKonten`, `vorschlag` lesen sich in der Domäne natürlicher;
  Room-Entities und Compose-Signaturen bleiben beim üblichen Englisch.
