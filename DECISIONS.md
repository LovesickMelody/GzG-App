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

## Sonstiges

- **Backup erlaubt, Bonfotos eingeschlossen** — bei Geräteswechsel sollen Belege
  mitkommen; der Export-Cache ist ausgenommen.
- **Deutsche Bezeichner in der App-eigenen Fachlogik, englische in Framework-Nähe** —
  `pruefeKonto`, `belegteKonten`, `vorschlag` lesen sich in der Domäne natürlicher;
  Room-Entities und Compose-Signaturen bleiben beim üblichen Englisch.
