# GZG-Tracker

Android-App, um Geld-zurück-Garantie-Aktionen („gratis testen“) zu finden und den
Erstattungsstatus der eigenen Einreichungen zu verfolgen.

Alles bleibt auf dem Gerät: kein Backend, keine Konten, keine Analyse, keine Tracker.
Nur zwei Berechtigungen — Kamera für den Barcode-Scan und Internet für den Aktions-Feed.
Bonfotos kommen über den Photo Picker herein, deshalb braucht die App keinen
Speicherzugriff.

**Die App reicht nichts automatisch bei Anbietern ein.** Sie öffnet die Aktionsseite,
die Einreichung machst du dort selbst, danach setzt du den Status in der App.

---

## Inhalt

- [APK aufs Handy bringen](#apk-aufs-handy-bringen)
- [Wie die App aufgebaut ist](#wie-die-app-aufgebaut-ist)
- [Der Aktions-Feed](#der-aktions-feed)
- [Erstanbieter statt Portale](#erstanbieter-statt-portale)
- [Einen kaputten Scraper reparieren](#einen-kaputten-scraper-reparieren)
- [Eine neue Quelle hinzufügen](#eine-neue-quelle-hinzufügen)
- [Entwickeln und Testen](#entwickeln-und-testen)
- [Release signieren](#release-signieren)
- [Rechtliches zum Scraping](#rechtliches-zum-scraping)

---

## APK aufs Handy bringen

Jeder Push baut die App. Es gibt zwei Wege an die Datei — nimm den, der gerade
funktioniert.

### Weg 1: Prerelease „debug-latest“ (empfohlen)

Der bequemste Weg, weil er direkt am Handy klappt:

1. Am Handy **Releases** des Repositorys öffnen:
   `https://github.com/LovesickMelody/GzG-App/releases`
2. Beim Eintrag **„Debug-APK (letzter Stand)“** die Datei
   `gzg-tracker-debug.apk` antippen.
3. Nach dem Download auf die Datei tippen und der Installation zustimmen
   (siehe [Unbekannte Quellen](#unbekannte-quellen-erlauben)).

Dieses Prerelease wird bei jedem Push überschrieben und zeigt immer den letzten Stand.

### Weg 2: Artifact aus dem Actions-Lauf

1. Im Repository auf **Actions** → Workflow **Android** → den obersten grünen Lauf.
2. Ganz unten unter **Artifacts** auf `gzg-tracker-debug-apk` tippen — es lädt eine
   ZIP-Datei.
3. ZIP entpacken, die enthaltene `.apk` installieren.

> **Wenn der Artifact-Upload fehlschlägt:** Meist ist das Artifact-Speicherkontingent
> des GitHub-Kontos voll („Artifact storage quota has been hit“). Der Build selbst ist
> davon nicht betroffen — nimm dann Weg 1. Aufräumen kannst du unter
> *Settings → Billing → Storage*, indem du alte Artifacts anderer Repositorys löschst.

### Unbekannte Quellen erlauben

Android verlangt beim ersten Mal eine Freigabe. Ab Android 8 gilt sie pro App:

1. Die APK antippen — Android fragt nach.
2. Auf **Einstellungen** tippen.
3. Bei der App, die den Download geöffnet hat (Chrome, Dateien oder Firefox),
   **„Installieren von Apps erlauben“** einschalten.
4. Zurück und die Installation bestätigen.

Falls die Nachfrage ausbleibt: *Einstellungen → Apps → Spezieller App-Zugriff →
Unbekannte Apps installieren*.

Die Debug-APK trägt die Kennung `de.gzgtracker.debug` und lässt sich damit parallel zu
einer späteren Release-Version installieren.

---

## Wie die App aufgebaut ist

```
GzG-App/
├── app/           Android-App (Compose, Room, Hilt)
├── core/          reines Kotlin/JVM: Domänenmodelle und Regeln, ohne Android
├── scraper/       Python: sammelt die Aktionen (läuft in GitHub Actions)
├── data/
│   └── actions.json   Ergebnis des Scrapers, von der App geladen
└── .github/workflows/
    ├── android.yml    Build, Tests, APK
    └── scrape.yml     täglicher Scrape-Lauf
```

### Warum zwei Module

`core` enthält die Regeln, auf die es ankommt — Beträge, Kontoverteilung,
Duplikatsprüfung, Summen, Filter, CSV. Ohne Android-Abhängigkeiten laufen die Tests
dort in Sekunden und ohne Android SDK. `app` enthält alles, was ein Gerät braucht.

### Schichten in `app`

```
ui/…          Compose-Screens + ViewModel je Screen (MVVM)
data/repository   Repositories — die einzige Tür zwischen UI und Daten
data/local        Room: Entities, DAOs, Mapper
data/remote       Retrofit: actions.json
data/settings     DataStore: Kontoregel, Feed-URL
di/               Hilt-Module
```

Geldbeträge sind **immer `Int` in Cent**. Nie Float, nie Double.

### Datenmodell

| Tabelle | Inhalt |
|---|---|
| `accounts` | Zielkonten: Name, letzte 4 IBAN-Stellen, Farbe, aktiv |
| `promo_actions` | Aktionen aus dem Feed und von Hand angelegte |
| `submissions` | Gekaufte Produkte mit Status, Bonpfad und Zielkonto |

Einreichungen haben bewusst **keinen** Fremdschlüssel auf Aktionen: Aktionen kommen und
gehen mit dem Feed, eine Einreichung überlebt das Verschwinden ihrer Aktion.

### Die Kontoregel

Pro Aktion darf jedes Konto nur einmal Erstattungsziel sein. Die App

- **warnt oder blockiert** beim Speichern (umschaltbar unter *Einstellungen → Kontoregel*),
- **schlägt automatisch** ein freies Konto vor — das, welches insgesamt am längsten
  nicht dran war (Round-Robin),
- zeigt je Konto, was noch aussteht.

Eine **abgelehnte** Einreichung gibt das Konto wieder frei: Es ist kein Geld geflossen,
ein zweiter Anlauf über dasselbe Konto ist legitim.

### Statusfarben

| Status | Bedeutung | Farbe |
|---|---|---|
| `GEKAUFT` | gekauft, noch nicht eingereicht | neutral |
| `EINGEREICHT` | Bon abgeschickt | gelb |
| `ERSTATTET` | Geld ist da | grün |
| `ABGELEHNT` | Anbieter hat abgelehnt | rot |

Farbe ist ausschließlich für Status reserviert — Buttons und Navigation bleiben `ink`.
Jeder Status trägt zusätzlich Icon und Text, nie nur Farbe.

---

## Der Aktions-Feed

Das Scraping läuft **nicht in der App**, sondern als GitHub-Actions-Job. Ergebnis ist
`data/actions.json` im Repo. Die App lädt diese Datei per HTTPS, legt sie in Room ab und
funktioniert danach offline mit dem letzten Stand. Pull-to-Refresh holt neu.

### Welche Quellen gesammelt werden

| Quelle | Was sie liefert |
|---|---|
| [geldzurueck.deals](https://geldzurueck.deals/) | Portal ausschließlich für Geld-zurück-Aktionen. Titel, Art (gratis testen / Cashback), Bild, Link. Ohne Datum: die Seite zeigt nur eine Restlaufzeit als Countdown. |
| [rabattigel.de/cashback](https://rabattigel.de/cashback/) | Aktionen mit **echtem Einsendeschluss** und Aktionszeitraum. |

Zwei Quellen sind Absicht: Fällt eine aus, bleibt der Feed gefüllt.

Die beiden Portale, die ursprünglich vorgesehen waren, gibt es nicht mehr —
`www.gratis-testen.de` antwortet nicht mehr, `www.aktion-gratis-testen.de` löst nicht
einmal im DNS auf. Welche Portale sonst noch geprüft wurden und warum sie ausschieden,
steht mit Begründung je Adresse in [`scraper/kandidaten.txt`](scraper/kandidaten.txt).

### Das Repository muss öffentlich sein

`raw.githubusercontent.com` liefert Dateien aus **privaten** Repositorys nicht ohne
Zugangsdaten aus — und ein Zugangstoken hätte in einer APK nichts zu suchen, jeder
könnte ihn dort auslesen. Ist das Repository privat, meldet die App beim Aktualisieren
deshalb „Kein Zugriff auf den Feed“.

Umstellen unter *Settings → General → ganz unten „Danger Zone“ → Change repository
visibility → Make public*. Was dabei öffentlich wird: der App-Code, die gesammelten
Aktionsdaten und die Git-Historie samt der Commit-Adressen. Was **nicht** im
Repository liegt und daher auch nicht öffentlich wird: deine Einreichungen, Bonfotos
und Konten — die bleiben ausschließlich auf dem Gerät.

Nebeneffekt: Öffentliche Repositorys haben unbegrenzte Actions-Minuten und ein
großzügigeres Artifact-Kontingent.

Wenn das Repository doch privat bleiben soll, gibt es zwei Wege:

- unter *Einstellungen → Feed-URL* eine frei erreichbare Adresse eintragen (etwa einen
  öffentlichen Gist, den der Scrape-Workflow mitpflegt), **oder**
- ohne Feed arbeiten und Aktionen von Hand anlegen — das geht ohnehin jederzeit.

### Der tägliche Lauf startet erst nach dem Merge nach `main`

GitHub startet zeitgesteuerte Workflows (`schedule`) und manuelle Läufe
(`workflow_dispatch`) **nur für Workflow-Dateien auf dem Standardbranch**. Solange
`scrape.yml` nur auf einem Feature-Branch liegt, taucht „Aktionen sammeln“ nicht unter
*Actions* auf und der nächtliche Lauf passiert nicht. Nach dem Merge nach `main` läuft
beides von selbst.

`push` gilt dagegen auf **jedem** Branch. Deshalb läuft `scrape.yml` zusätzlich bei
jeder Änderung unter `scraper/**` — vor dem Merge ist das der einzige Weg, den
Sammellauf überhaupt zu starten, und danach sieht man eine geänderte Quelle sofort,
statt bis zum nächsten Morgen zu warten. `data/**` löst bewusst nichts aus, sonst
stieße der eigene Commit den nächsten Lauf an.

### Ablauf des Jobs

1. `scrape.yml` läuft täglich um 04:00 UTC, oder von Hand über *Actions → Aktionen
   sammeln → Run workflow*.
2. Erst laufen die Parser-Tests gegen gespeicherte HTML-Fixtures.
3. Dann werden die Quellen aus `scraper/sources.yaml` abgeklappert.
4. `data/actions.json` wird **nur bei inhaltlicher Änderung** committet.

Fällt eine Quelle aus, bleibt deren bisheriger Stand erhalten und der Job wird trotzdem
grün — sonst wäre jede Portalwartung ein Fehlalarm. Rot wird er erst, wenn **keine**
Quelle mehr etwas liefert.

---

## Erstanbieter statt Portale

Die Portalquellen oben haben zwei Schwächen, die zusammengehören: Jede
Seitenumgestaltung bricht die Selektoren, und die Daten stammen aus einer
fremden, redaktionell gepflegten Sammlung. Die Quellenart `erstanbieter` löst
beides, indem sie die Kampagne dort liest, wo sie entsteht — beim Hersteller
beziehungsweise bei seinem Abwickler.

```
Entdeckung (CT-Logs, Sitemap)  →  Kandidaten-Adressen   kein Portal beteiligt
        ↓
Abruf + Vorbehaltsprüfung
        ↓
Extraktion (JSON-LD, sonst Modell)                      kein Selektor beteiligt
        ↓
Prüfschicht                                             was nicht belegt ist, fliegt raus
```

### Entdeckung: Kampagnen finden sich von selbst

Aktionsplattformen legen je Kampagne eine eigene Subdomain an — belegt für
JustSnap durch die Air-Wick-Aktion, deren Kontaktadresse
`kontakt@airwick.justsnap.eu` lautet. Jede neue Subdomain braucht ein
TLS-Zertifikat, und jedes ausgestellte Zertifikat landet nach RFC 6962 in einem
öffentlichen Protokoll. Eine Abfrage am Tag genügt:

```
https://crt.sh/?q=%25.justsnap.eu&output=json
```

Das ist rein passiv: öffentliche Register lesen, keine Anfrage an die
Zielsysteme, kein Erraten von Namen. Plattformen, die ihre Kampagnen über
*Pfade* statt Subdomains führen, fängt stattdessen die `sitemap.xml` ab —
deshalb gibt es beide Entdecker.

### Extraktion: ein Weg für alle Seiten

1. **JSON-LD zuerst.** Viele Kampagnenseiten betten ihre Eckdaten als
   `schema.org`-Daten für Suchmaschinen ein. Exakt, kostenlos, vom Anbieter
   selbst gepflegt.
2. **Modell als Auffanglösung.** Was kein JSON-LD hat, geht als Text an ein
   Sprachmodell mit festem JSON-Schema. Ein Prompt für sämtliche Quellen.

**Der Betrag kommt in beiden Wegen aus dem sichtbaren Seitentext**, gelesen von
`parsing.betrag_in_cent` — derselben Funktion, die schon die Portale auswertet
und die weiß, dass „0,75 l" kein Geldbetrag ist. Das Modell darf zitieren, aber
nicht rechnen; JSON-LD liefert den *Laden*preis, nicht die Erstattung.

### Die Prüfschicht

`pruefung.py` entscheidet, was veröffentlicht wird. Jede Regel verhindert einen
konkreten Schaden:

| Regel | Was ohne sie passiert |
|---|---|
| `betrag_belegt` | „4,99 € zurück" steht im Feed, der Hersteller erstattet 2 €. Wer deshalb eingekauft hat, verliert Geld wegen unserer Angabe. |
| `gestartet` | Die CT-Entdeckung findet Kampagnen, sobald ihr Zertifikat existiert — oft Wochen vor dem Start. Anzeigen verrät die Planung des Herstellers. Gilt **nur** für entdeckte Quellen — was mydealz ankündigt, ist veröffentlicht und darf in die Merkliste; die App weist es als „Startet in 2 Tagen" aus, damit niemand zu früh kauft. |
| `kein_vorbehalt` | Wir werten eine Quelle aus, die das untersagt hat (§ 44b UrhG). |
| `frist_plausibel` | Ein verlesenes Datum hält eine tote Aktion für immer in der Liste. |
| `pflichtfelder` | Leere Zeile in der App, die sich nicht öffnen lässt. |

Zur Vorbehaltsprüfung: Automatisiertes Auswerten ist nach § 44b UrhG erlaubt,
solange der Rechteinhaber es nicht maschinenlesbar untersagt hat — und das
LG Hamburg hat im Verfahren Kneschke ./. LAION entschieden, dass ein solcher
Vorbehalt **auch in natürlicher Sprache** wirksam erklärt werden kann. Die
`robots.txt` allein zu prüfen genügt seitdem nicht. `tdm.py` sieht deshalb
zusätzlich in `/.well-known/tdmrep.json`, in den Meta-Angaben und im Seitentext
nach. Die Erkennung ist bewusst schief eingestellt: Ein falscher Alarm kostet
eine Quelle, ein übersehener Vorbehalt die Rechtsgrundlage.

### Eine Erstanbieter-Quelle anlegen

Kein Selektor, nur die Plattform:

```yaml
- name: justsnap
  enabled: true
  parser: erstanbieter
  ct_logs:
    - justsnap.eu            # nackte Domain; gefragt wird "%.justsnap.eu"
  sitemaps:
    - url: https://justsnap.eu/sitemap.xml
      muster: "/aktion/"     # ohne Muster kommt die halbe Seite zurück
  max_kandidaten: 40         # Abrufe je Lauf; neueste Zertifikate zuerst
```

Erst probelaufen lassen und **das Ergebnis lesen, nicht nur zählen**:

```bash
cd scraper
python -m gzg_scraper.run --only justsnap --output /tmp/probe.json --delay 3
```

### Das Modell einrichten

Ohne Schlüssel überspringt der Lauf die Modell-Extraktion, meldet das einmal und
bleibt grün — Quellen mit JSON-LD laufen weiter. Für den vollen Umfang:

*Settings → Secrets and variables → Actions → New repository secret* mit dem
Namen `ANTHROPIC_API_KEY`. Modell und Denktiefe lassen sich über die
*Variables* `GZG_MODELL` und `GZG_EFFORT` umstellen, ohne den Code anzufassen:

```bash
python -m gzg_scraper.run --modell claude-haiku-4-5 --effort low
python -m gzg_scraper.run --ohne-modell     # nur JSON-LD, kostet nichts
```

Vorgabe ist `claude-opus-5` bei Denktiefe `low`. Das ist die teure, sichere
Variante; ein kleineres Modell reicht für reines Abschreiben aus vorliegendem
Text oft aus und kostet einen Bruchteil. Die Entscheidung gehört dir — die
Prüfschicht dahinter ist dieselbe.

---

## Einen kaputten Scraper reparieren

> Gilt für die Portalquellen mit CSS-Selektoren. Erstanbieter-Quellen haben
> keine Selektoren und damit auch nichts, was ein Seitenumbau brechen könnte.

Typisches Symptom: Der Job ist grün, aber im Log steht

```
ERROR gzg_scraper: Quelle rabattigel: Seiten geladen, aber keine Aktion erkannt —
Selektoren prüfen. Alter Stand bleibt.
```

Das heißt fast immer: Das Portal hat sein HTML umgebaut, die Selektoren in
`sources.yaml` treffen nicht mehr. Reparatur in vier Schritten:

### 1. Ansehen, wie die Seite jetzt aufgebaut ist

Ohne lokale Python-Installation, direkt über GitHub (setzt voraus, dass `scrape.yml`
auf `main` liegt — siehe oben):

*Actions → **Aktionen sammeln** → Run workflow* → Feld **inspect** auf den Quellennamen
setzen (z. B. `rabattigel`) → starten. Der Job schreibt die Struktur ins Log:

```
  Wiederkehrende Container (Kandidaten für `item`), Top 12:
     18×  div.deal-card
     18×  article.teaser

  Beispiel für div.deal-card:
    h3: h3.deal-card__title -> 'Duschgel gratis testen'
    a:  a.deal-card__link -> href='/aktion/duschgel'
    Klassen im Eintrag (Kandidaten für brand/max_refund/deadline):
      span.deal-card__brand
      span.deal-card__price
      time.deal-card__until

  Was die konfigurierten Selektoren treffen:
    item           'article': 0 Treffer   <-- trifft nichts
```

Lokal geht dasselbe mit:

```bash
cd scraper
pip install -r requirements.txt
python inspect_source.py --source rabattigel
```

**Klassennamen allein reichen nicht.** Ob ein Betrag im Text oder in einem Attribut
steht und welcher der fünf Links im Eintrag zur Aktion führt statt zu einem Anker auf
derselben Seite, sieht man erst am Markup:

```bash
python inspect_source.py --url https://rabattigel.de/cashback/ --roh "article.rgu-card"
```

Das gibt den rohen HTML-Code der ersten Treffer aus. Selektoren daraus **ablesen**, nicht
aus den Klassennamen raten — sonst schreibt der Scraper still Unsinn in den Feed.

### 2. `sources.yaml` anpassen

Die abgelesenen Namen eintragen:

```yaml
selectors:
  item: "div.deal-card"
  title: "h3.deal-card__title"
  link: "a.deal-card__link@href"
  brand: "span.deal-card__brand"
  max_refund: "span.deal-card__price"
  deadline: "time.deal-card__until@datetime"
```

`@attribut` liest ein Attribut statt des Textes, `"@data-href"` ohne Selektor davor eines
des Eintrags selbst. Fehlende Felder sind kein Fehler — Betrag, EANs und Händler werden
notfalls aus dem Text des Eintrags gelesen.

Zwei Angaben stehen neben `selectors`, nicht darin:

```yaml
titel_entfernen: '\s*\[[^\]]*\]\s*$'   # wiederkehrenden Zusatz aus dem Titel schneiden
retailers: ["dm", "Rossmann"]          # eigene Händlerliste statt der eingebauten
```

### 3. Gegenprüfen

Nochmal mit `inspect` laufen lassen. Am Ende steht jetzt:

```
  Parser-Ergebnis: 18 Aktionen
    - 'Duschgel gratis testen' | Marke='Nivea' | Betrag=399 | Frist=2026-10-14
```

Stimmen Beträge und Fristen, ist die Quelle repariert.

### 4. Fixture nachziehen (empfohlen)

Damit derselbe Umbau beim nächsten Mal auffällt, bevor die Daten weg sind:

```bash
python inspect_source.py --source rabattigel --speichern tests/fixtures/rabattigel.html
```

Und einen Test dazu in `scraper/tests/`. Die Fixtures machen die Tests offline
lauffähig — keine Netzabhängigkeit in CI.

### Wenn gar nichts hilft

Manche Portale bauen ihre Liste per JavaScript zusammen; dann steht im HTML nichts
Brauchbares (`inspect` meldet „keine wiederkehrenden Container“). Solche Quellen
brauchen einen eigenen Parser — oft gibt es eine JSON-Schnittstelle im Netzwerk-Tab des
Browsers, die sich direkter auslesen lässt. Bis dahin: `enabled: false` setzen, dann
stört die Quelle nicht weiter.

---

## Eine neue Quelle hinzufügen

0. **Erst prüfen, ob das Portal überhaupt taugt.** Adressen in
   `scraper/kandidaten.txt` eintragen und abklopfen:

   ```bash
   python scraper/probe_kandidaten.py
   ```

   Je Adresse kommt Erreichbarkeit, `robots.txt`, Feed- oder HTML-Aufbau und eine grobe
   Einschätzung, wie viel auf der Seite nach Geld-zurück-Aktion aussieht. Aussortieren
   lassen sich damit die drei häufigsten Enttäuschungen: tote Domains, Seiten, die ihre
   Liste erst per JavaScript nachladen (kein wiederkehrender Container, keine Beträge im
   Text), und Portale mit generierten Utility-Klassen, deren Selektoren jede
   Seitenumgestaltung zerbricht. Ergebnisse gehören als Kommentar in dieselbe Datei —
   die nächste Suche fängt sonst wieder bei null an.

   **Hat das Portal einen RSS- oder Atom-Feed, nimm den.** Ein Feed ist eine Zusage des
   Betreibers, maschinenlesbar zu bleiben; er überlebt Umgestaltungen und kostet weniger
   Last. Vorsicht bei WordPress: `…/feed/` liefert oft die *Kommentare* der Seite statt
   der Beiträge — das sieht man sofort an Titeln wie „Von: Burgfee53“.

   ```yaml
   - name: neues-portal
     parser: feed
     listing_urls: ["https://neues-portal.de/aktionen/feed/"]
     keywords: ["geld zurück", "gratis testen", "cashback"]   # nur passende Einträge
     ausschluss: ["gewinnspiel", "verlosung"]
     brand_trenner: ":"                                       # "Marke: Titel" auftrennen
   ```

1. **Struktur ansehen:**

   ```bash
   python scraper/inspect_source.py --url https://neues-portal.de/aktionen
   python scraper/inspect_source.py --url https://neues-portal.de/aktionen --roh "div.card"
   ```

2. **Eintrag in `scraper/sources.yaml`** anlegen — der auskommentierte Block
   `beispiel-portal` dient als Vorlage:

   ```yaml
   - name: neues-portal          # wird zu "source" in actions.json; nicht mehr ändern
     enabled: true
     base_url: https://neues-portal.de/
     listing_urls:
       - https://neues-portal.de/aktionen
     parser: css_listing
     selectors:
       item: "…"
       title: "…"
   ```

3. **Testen**, bevor etwas geschrieben wird:

   ```bash
   cd scraper
   python -m gzg_scraper.run --only neues-portal --output /tmp/probe.json
   cat /tmp/probe.json
   ```

4. **Ergebnis lesen, nicht nur zählen.** „19 Aktionen gefunden“ heißt nicht, dass sie
   stimmen. Beim ersten echten Lauf stand in einem Titel `[gratis testen, Geld zurück!]`
   und bei einer Flasche „0,75 l“ ein Erstattungsbetrag von 0,75 €. Beides sah im
   JSON völlig plausibel aus. Deshalb gibt der Workflow das Ergebnis lesbar ins Log —
   Titel, Art, Betrag, Frist, Link — und ein Blick dort gehört zu jeder neuen Quelle.

5. **Fixture und Test** ergänzen (siehe `tests/test_css_listing.py`,
   `tests/test_feed.py`).

`name` ist der Schlüssel fürs Aufräumen: Aktionen dieser Quelle werden bei jedem Lauf
ersetzt. Wird `name` später geändert, gelten die alten Einträge als verwaist und
verschwinden — außer es hängen Einreichungen daran.

### Eigener Parser statt Selektoren

Braucht ein Portal echte Logik (Detailseiten nachladen, JSON auswerten):

1. `scraper/gzg_scraper/parsers/mein_portal.py` mit
   `def parse(html: str, quelle: dict) -> list[Action]`
2. In `scraper/gzg_scraper/registry.py` eintragen:
   ```python
   PARSER = {
       "css_listing": css_listing.parse,
       "feed": feed.parse,
       "mein_portal": mein_portal.parse,
   }
   ```
3. In `sources.yaml` `parser: mein_portal` setzen.

---

## Entwickeln und Testen

### Android

```bash
./gradlew test           # Unit-Tests (core + app)
./gradlew assembleDebug  # Debug-APK
```

Die Tests der Domänenlogik brauchen kein Android SDK:

```bash
./gradlew :core:test --configure-on-demand
```

Deshalb hat das Root-`build.gradle.kts` bewusst keinen `plugins`-Block — sonst
bräuchte auch `:core` Zugriff auf Google Maven.

### Scraper

```bash
cd scraper
pip install -r requirements.txt
python -m pytest              # Parser-Tests gegen Fixtures, ohne Netz
python -m gzg_scraper.run --output /tmp/probe.json --delay 3
```

Nützliche Schalter: `--only <quelle>`, `--delay <sekunden>`, `--sources <datei>`.

### Was getestet ist

| Bereich | Wo |
|---|---|
| Beträge in Cent, deutsche Ein-/Ausgabe | `core/src/test/…/MoneyTest.kt` |
| Kontovorschlag und Duplikatsregel | `core/…/AccountDistributionTest.kt` |
| Summenberechnung | `core/…/TotalsCalculatorTest.kt` |
| Filter und Suche | `core/…/SubmissionFilteringTest.kt` |
| CSV-Export | `core/…/CsvExportTest.kt` |
| Scraper-Parser gegen HTML- und XML-Fixtures | `scraper/tests/` |
| Beträge: Datum, Füllmenge und lange Zahl sind kein Geld | `scraper/tests/test_parsing.py` |

---

## Release signieren

Ohne hinterlegten Keystore signiert der Release-Build mit dem Debug-Key. Die APK ist
installierbar, aber nicht für den Play Store geeignet und der Key wechselt bei jeder
Umgebung. Für einen dauerhaften Key:

### 1. Keystore erzeugen (lokal, einmalig)

```bash
keytool -genkeypair -v \
  -keystore gzg-release.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias gzg
```

> **Diese Datei niemals ins Repo committen** und sicher aufbewahren. Ist sie weg, lässt
> sich eine installierte App nicht mehr durch ein Update ersetzen. `.gitignore` sperrt
> `*.jks`, `*.keystore` und `keystore.properties` bereits.

### 2. Als GitHub Secrets hinterlegen

*Settings → Secrets and variables → Actions → New repository secret*:

| Secret | Inhalt |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 gzg-release.jks` |
| `KEYSTORE_PASSWORD` | Passwort des Keystores |
| `KEY_ALIAS` | `gzg` |
| `KEY_PASSWORD` | Passwort des Schlüssels |

### 3. Release bauen

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Der Workflow baut die Release-APK und hängt sie an ein GitHub Release.

### Lokal signieren

Statt Secrets geht auch eine `keystore.properties` im Projektwurzelverzeichnis
(nicht im Repo):

```properties
storeFile=/pfad/zu/gzg-release.jks
storePassword=…
keyAlias=gzg
keyPassword=…
```

---

## Rechtliches zum Scraping

Das automatisierte Auslesen fremder Seiten kann gegen deren Nutzungsbedingungen
verstoßen — auch dann, wenn die Seite technisch frei erreichbar ist. Das ist deine
Entscheidung und dein Risiko.

Der Scraper hält die Last so klein wie möglich:

- er fragt die `robots.txt` und hält sich daran,
- er schickt einen ehrlichen User-Agent mit Projektlink statt sich als Browser zu tarnen,
- er wartet standardmäßig 3 Sekunden zwischen zwei Abrufen desselben Hosts,
- er läuft einmal täglich, nicht laufend,
- er speichert das Ergebnis einmal zentral, statt dass jede App-Installation selbst lädt.

Bittet ein Betreiber darum, seine Seite nicht auszulesen: `enabled: false` in
`sources.yaml`, fertig.

Die gesammelten Daten sind reine Sachangaben (Produkt, Marke, Betrag, Frist) und werden
unverändert weitergereicht. Bilder werden nur verlinkt, nicht kopiert.

---

## Lizenzen Dritter

Die Schriften Archivo, Inter und JetBrains Mono liegen als Latin-Subset im APK. Alle
drei stehen unter der SIL Open Font License 1.1, die Lizenztexte liegen unter
[`licenses/`](licenses/).
