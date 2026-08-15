# Arbeitsliste GZG-Tracker

Abgehakt heisst: gebaut, getestet, committet.

## Phase 1 — Projektgeruest + CI

- [x] Gradle-Setup (Kotlin DSL, Version Catalog, Wrapper 8.11.1)
- [x] Modulschnitt `:app` (Android) und `:core` (reines Kotlin/JVM)
- [x] `Money` — Cent-Formatierung und -Eingabe inkl. Tests
- [x] Manifest, Berechtigungen (nur Kamera + Internet), FileProvider
- [x] Adaptive Launcher-Icon im Bon-Look
- [x] `.github/workflows/android.yml` — Debug-APK + Tests, Release bei Tag `v*`
- [x] Erster gruener CI-Lauf

## Phase 2 — Datenmodell, Room, Design-System

- [x] Domaenenmodelle in `:core` (Account, PromoAction, Submission, Status)
- [x] Room-Entities, DAOs, Datenbank, Konverter
- [x] Hilt-Module (Datenbank, Repositories, Netzwerk)
- [x] Farb-Tokens `paper` / `ink` / `inkMuted` / Statusfarben, Hell und Dunkel
- [x] Typografie Archivo / Inter / JetBrains Mono (Downloadable Fonts)
- [x] Dynamic Color deaktiviert
- [x] Signature-Komponenten: Belegzeile, Stempel-Badge, Abrisskante

## Phase 3 — Konten und Verteilungsregel

- [x] Kontenverwaltung: anlegen, bearbeiten, deaktivieren
- [x] Kontovorschlag (Round-Robin ueber laengste Nichtnutzung)
- [x] Duplikatsregel je Aktion, umschaltbar warnen/blockieren
- [x] Summen und offene Posten je Konto
- [x] Tests fuer Vorschlag und Duplikatspruefung

## Phase 4 — Aktions-Feed

- [x] `scraper/` mit Quellen-Registry und einem Parser je Portal
- [x] `scraper/sources.yaml`
- [x] Stabiler Hash fuer Aktions-IDs
- [x] `data/actions.json` erzeugen, nur bei Aenderung committen
- [x] `.github/workflows/scrape.yml` (taeglich 04:00 UTC + manuell)
- [x] Live-Struktur der Portale pruefen und Selektoren daraus ableiten
      (beide Portale der Aufgabenstellung sind tot; 17 Kandidaten abgeklopft,
      geldzurueck.deals und rabattigel.de uebernommen, Selektoren am Rohbau abgelesen —
      19 echte Aktionen in `data/actions.json`)
- [x] Feed-Parser fuer RSS/Atom als Alternative zu CSS-Selektoren
- [x] mydealz-Gruppe "Geld zurueck" als RSS-Quelle
- [x] Nur volle Erstattungen sammeln, Teilbetraege aussortieren
- [x] Abgelaufene Aktionen aussortieren
- [x] `submit_url`: Link zur Einreichungsseite statt nur zum Portalartikel
- [x] `requirements`: Checkliste "Was brauche ich?" aus den Teilnahmebedingungen
- [x] Checkliste und Einreichungs-Knopf in der App
- [x] Erfassung mit mehreren Fotos (Produkt, Bon, beides zusammen) statt nur Bonfoto
- [x] Scannen als Nebenweg statt als Haupteinstieg
- [x] Dieselbe Aktion aus zwei Quellen zusammenfassen (ueber gleiche `submit_url`)
- [x] Merkliste: Aktionen vormerken und im Laden abhaken
- [x] Belegfoto: Ladefehler behoben, Kamera als Quelle dazu
- [x] Preis und Kaufdatum aus dem Bon lesen (Texterkennung auf dem Geraet)
- [x] Produktbild aus dem Feed in der Aktionsliste
- [x] Einreichungslink auch fuer mydealz
- [x] App laedt `actions.json`, cached in Room, Pull-to-Refresh, offline nutzbar
- [x] Aktion manuell anlegen
- [x] Parser-Tests gegen HTML-Fixtures (offline)

## Phase 5 — Scannen und Erfassen

- [x] Barcode-Scan (CameraX + ML Kit, EAN-13/EAN-8)
- [x] EAN gegen Aktionsliste matchen
- [x] Erfassungsformular inkl. Zielkonto und Kontowarnung
- [x] Bonfoto: Kamera und Photo Picker, app-intern gespeichert
- [x] "Aktionsseite oeffnen"

## Phase 6 — Uebersicht

- [x] Liste, neueste zuerst
- [x] Filter Status / Konto / Zeitraum / Aktion, Suche nach Produktname
- [x] Summenkarte mit Abrisskante
- [x] Detailansicht mit Bon in gross und Statusverlauf
- [x] Statuswechsel per Tap und Swipe, Stempel-Animation bei ERSTATTET
- [x] CSV-Export ueber das Share-Sheet

## Phase 7 — Abschluss

- [x] Tests gruen, CI gruen
- [x] README: Setup, Architektur, APK installieren, Scraper reparieren, Quelle ergaenzen
- [x] `DECISIONS.md` vollstaendig

## Nachbesserungen aus dem Gerätetest

- [x] Preis: Posten des Aktionsprodukts statt Bonsumme
- [x] Händler aus der Bon-Kopfzeile vorbelegen
- [x] Leere Seite nach "Speichern und einreichen": Ladebalken, Fehlermeldung,
      Browser-Kennung ohne "wv", Ausweg "Im Browser öffnen"
- [x] "Zur Einreichung"-Knopf und die Artikelvorschläge aus der Erfassung entfernt
- [x] Datei-Feld der Anbieterseite: eigene Fotos zur Auswahl statt Totstellen
- [x] Zurück-Taste im Formular: erst im Verlauf zurück, dazu ein sichtbares "Fertig"
- [x] Abgebrochene Einreichung: "Nochmal einreichen" in der Detailansicht
- [x] mydealz-Zwischenseite beim Sammeln auflösen
- [x] "Speichern und einreichen" setzt den Status auf eingereicht

