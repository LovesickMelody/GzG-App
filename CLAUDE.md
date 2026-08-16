# Projektregeln für Claude Code

Diese Datei gilt für jede Session in diesem Repo.

## Autonomie

- Arbeite Aufgaben vollständig durch, ohne zwischendurch nach Bestätigung zu fragen.
- Offene Detailfragen entscheidest du selbst nach bestem Ermessen und dokumentierst die
  Entscheidung in einer Zeile in `DECISIONS.md`. Nicht nachfragen.
- Führe `TODO.md` als Arbeitsliste und hake erledigte Punkte ab.
- Unterbrich mich nur bei: benötigten Secrets/Zugängen, grundlegenden Änderungen am Datenmodell
  oder an der Kontoregel, oder wenn du nach zwei ernsthaften Versuchen am selben Fehler feststeckst.

## Qualität

- Nach jeder Arbeitsphase selbst `./gradlew assembleDebug` und `./gradlew test` ausführen und
  Fehler eigenständig beheben, bevor du weitermachst.
- Neue Logik bekommt Tests. Besonders: Kontovorschlag, Duplikatsprüfung, Summenberechnung,
  Scraper-Parser (gegen gespeicherte HTML-Fixtures, offline lauffähig).
- Geldbeträge immer als `Int` in Cent, nie als Float oder Double.

## Design

- Farbe hat genau drei Rollen, und keine Farbe hat zwei davon:
  1. **Status** — gelb = eingereicht, grün = erstattet, rot = abgelehnt. Nur als Fläche,
     immer mit Icon und Text daneben. Rot darf zusätzlich als *Textfarbe* eine
     ablaufende Frist markieren, nie als Fläche.
  2. **Interaktion** — der Akzent (tiefes Tintenblau): primäre Knöpfe, aktiver Reiter,
     Ausgewähltes, Fokus.
  3. **Struktur** — Tinte auf Papier: Text, Flächen, Linien.
- Pro Bildschirm genau eine primäre Handlung. Drei Stufen: primär gefüllt im Akzent,
  sekundär `OutlinedButton`, tertiär `TextButton`.
- Material You Dynamic Color bleibt deaktiviert.
- Keine neuen Farben oder Schriftgrößen außerhalb der Tokens in `ui/theme/`.
- Touch-Ziele mindestens 48 dp. Beschriftungen brechen nicht mitten im Wort um.
- UI-Texte auf Deutsch, Sentence Case, aktive Verben.

## Git

- Nach jeder Phase committen, mit aussagekräftiger Message.
- Kein `git push --force`. Keine Dateien außerhalb des Projektordners anfassen.
- Niemals Keystores, Passwörter oder Tokens ins Repo committen.
