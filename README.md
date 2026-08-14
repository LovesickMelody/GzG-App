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

### Erst nach dem Merge nach `main` aktiv

GitHub startet zeitgesteuerte Workflows (`schedule`) und manuelle Läufe
(`workflow_dispatch`) **nur für Workflow-Dateien auf dem Standardbranch**. Solange
`scrape.yml` nur auf einem Feature-Branch liegt, taucht „Aktionen sammeln“ nicht unter
*Actions* auf und der tägliche Lauf passiert nicht. Nach dem Merge nach `main` läuft
beides von selbst.

Bis dahin lässt sich der Scraper lokal starten (siehe
[Entwickeln und Testen](#entwickeln-und-testen)).

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

## Einen kaputten Scraper reparieren

Typisches Symptom: Der Job ist grün, aber im Log steht

```
ERROR gzg_scraper: Quelle gratis-testen: Seiten geladen, aber keine Aktion erkannt —
Selektoren prüfen. Alter Stand bleibt.
```

Das heißt fast immer: Das Portal hat sein HTML umgebaut, die Selektoren in
`sources.yaml` treffen nicht mehr. Reparatur in vier Schritten:

### 1. Ansehen, wie die Seite jetzt aufgebaut ist

Ohne lokale Python-Installation, direkt über GitHub (setzt voraus, dass `scrape.yml`
auf `main` liegt — siehe oben):

*Actions → **Aktionen sammeln** → Run workflow* → Feld **inspect** auf den Quellennamen
setzen (z. B. `gratis-testen`) → starten. Der Job schreibt die Struktur ins Log:

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
python inspect_source.py --source gratis-testen
```

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

`@attribut` liest ein Attribut statt des Textes. Fehlende Felder sind kein Fehler —
Betrag, EANs und Händler werden notfalls aus dem Text des Eintrags gelesen.

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
python inspect_source.py --source gratis-testen --speichern tests/fixtures/gratis_testen.html
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

1. **Struktur ansehen:**

   ```bash
   python scraper/inspect_source.py --url https://neues-portal.de/aktionen
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

4. **Fixture und Test** ergänzen (siehe `tests/test_css_listing.py`).

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
| Scraper-Parser gegen HTML-Fixtures | `scraper/tests/` |

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
