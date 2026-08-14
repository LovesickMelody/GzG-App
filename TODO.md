# Arbeitsliste GZG-Tracker

Abgehakt heisst: gebaut, getestet, committet.

## Phase 1 — Projektgeruest + CI

- [x] Gradle-Setup (Kotlin DSL, Version Catalog, Wrapper 8.11.1)
- [x] Modulschnitt `:app` (Android) und `:core` (reines Kotlin/JVM)
- [x] `Money` — Cent-Formatierung und -Eingabe inkl. Tests
- [x] Manifest, Berechtigungen (nur Kamera + Internet), FileProvider
- [x] Adaptive Launcher-Icon im Bon-Look
- [x] `.github/workflows/android.yml` — Debug-APK + Tests, Release bei Tag `v*`
- [ ] Erster gruener CI-Lauf

## Phase 2 — Datenmodell, Room, Design-System

- [ ] Domaenenmodelle in `:core` (Account, PromoAction, Submission, Status)
- [ ] Room-Entities, DAOs, Datenbank, Konverter
- [ ] Hilt-Module (Datenbank, Repositories, Netzwerk)
- [ ] Farb-Tokens `paper` / `ink` / `inkMuted` / Statusfarben, Hell und Dunkel
- [ ] Typografie Archivo / Inter / JetBrains Mono (Downloadable Fonts)
- [ ] Dynamic Color deaktiviert
- [ ] Signature-Komponenten: Belegzeile, Stempel-Badge, Abrisskante

## Phase 3 — Konten und Verteilungsregel

- [ ] Kontenverwaltung: anlegen, bearbeiten, deaktivieren
- [ ] Kontovorschlag (Round-Robin ueber laengste Nichtnutzung)
- [ ] Duplikatsregel je Aktion, umschaltbar warnen/blockieren
- [ ] Summen und offene Posten je Konto
- [ ] Tests fuer Vorschlag und Duplikatspruefung

## Phase 4 — Aktions-Feed

- [ ] `scraper/` mit Quellen-Registry und einem Parser je Portal
- [ ] `scraper/sources.yaml`
- [ ] Stabiler Hash fuer Aktions-IDs
- [ ] `data/actions.json` erzeugen, nur bei Aenderung committen
- [ ] `.github/workflows/scrape.yml` (taeglich 04:00 UTC + manuell)
- [ ] Live-Struktur der Portale pruefen und Selektoren daraus ableiten
- [ ] App laedt `actions.json`, cached in Room, Pull-to-Refresh, offline nutzbar
- [ ] Aktion manuell anlegen
- [ ] Parser-Tests gegen HTML-Fixtures (offline)

## Phase 5 — Scannen und Erfassen

- [ ] Barcode-Scan (CameraX + ML Kit, EAN-13/EAN-8)
- [ ] EAN gegen Aktionsliste matchen
- [ ] Erfassungsformular inkl. Zielkonto und Kontowarnung
- [ ] Bonfoto: Kamera und Photo Picker, app-intern gespeichert
- [ ] "Aktionsseite oeffnen"

## Phase 6 — Uebersicht

- [ ] Liste, neueste zuerst
- [ ] Filter Status / Konto / Zeitraum / Aktion, Suche nach Produktname
- [ ] Summenkarte mit Abrisskante
- [ ] Detailansicht mit Bon in gross und Statusverlauf
- [ ] Statuswechsel per Tap und Swipe, Stempel-Animation bei ERSTATTET
- [ ] CSV-Export ueber das Share-Sheet

## Phase 7 — Abschluss

- [ ] Tests gruen, CI gruen
- [ ] README: Setup, Architektur, APK installieren, Scraper reparieren, Quelle ergaenzen
- [ ] `DECISIONS.md` vollstaendig
