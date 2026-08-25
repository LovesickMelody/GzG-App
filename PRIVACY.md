# Datenschutzerklärung — GZG-Tracker

**Stand: 25. August 2026**

> **Hinweis für den Betreiber:** Dieser Text beschreibt, was die App laut ihrem
> Quelltext tatsächlich tut — abgeleitet aus dem Datenmodell, den Berechtigungen
> und den Netzwerkzugriffen, nicht aus einer Vorlage. Vor einer Veröffentlichung
> im Play Store müssen die mit **[AUSFÜLLEN]** markierten Angaben zum
> Verantwortlichen ergänzt und der Text von einer juristisch qualifizierten
> Person geprüft werden. Er ersetzt keine Rechtsberatung.

## Kurzfassung

GZG-Tracker speichert **alle** Daten ausschließlich auf deinem Gerät. Es gibt
keinen Server des Anbieters, kein Nutzerkonto, keine Analyse-Werkzeuge und keine
Tracker. Deine Bankverbindung, deine Kassenbons und deine Einreichungen
verlassen dein Telefon nur dann, wenn **du** sie selbst an einen Anbieter
übermittelst.

## Verantwortlicher

**[AUSFÜLLEN: Name, Anschrift, E-Mail-Adresse des Verantwortlichen]**

## Welche Daten die App speichert

Alle folgenden Daten liegen in einer Datenbank auf deinem Gerät und im
app-eigenen Dateibereich. Ein Zugriff darauf durch den Anbieter ist technisch
nicht möglich.

### Konten (von dir angelegt)

Name, Anrede, Vorname, Nachname, Geburtsdatum, Straße, Hausnummer, Postleitzahl,
Ort, Telefonnummer, E-Mail-Adresse, **IBAN** und **BIC**.

Diese Angaben dienen einem einzigen Zweck: Sie werden in die Einreichungsformulare
der Aktionsanbieter eingetragen, damit du sie nicht bei jeder Aktion abtippen
musst.

### Einreichungen

Produktname, EAN, gezahlter Preis, Kaufdatum, Händler, Status der Einreichung,
Datum von Einreichung und Erstattung, erstatteter Betrag und eine freiwillige
Notiz — dazu die Zuordnung zu einem deiner Konten.

### Fotos

Fotos von Kassenbons, Produkten und Kombiaufnahmen, die du selbst aufnimmst oder
aus deiner Galerie auswählst. Sie liegen im app-eigenen Bereich und sind für
andere Apps nicht sichtbar.

### Aktionsdaten

Die Liste der Rabattaktionen samt Merkliste und gestellten Erinnerungen. Diese
Daten stammen aus einer öffentlichen Datei und enthalten nichts über dich.

## Wohin die App Verbindungen aufbaut

Die App baut von sich aus **genau eine** Art von Verbindung auf:

1. **Abruf der Aktionsliste** von
   `raw.githubusercontent.com/LovesickMelody/GzG-App/main/data/actions.json`.
   Dabei werden keine Daten über dich übertragen. Wie bei jedem Abruf im Internet
   sieht der Betreiber (GitHub, Inc.) technisch bedingt deine IP-Adresse; die
   Datenschutzhinweise von GitHub gelten insoweit ergänzend.
2. **Laden der Produktbilder** von den Servern der jeweiligen Quelle. Auch hier
   wird lediglich das Bild abgerufen.

Darüber hinaus öffnet die App die **Einreichungsseite des jeweiligen Anbieters**
in einem eingebetteten Browser — aber nur, wenn du auf „Einreichen" tippst. Was
du dort einträgst und absendest, geht an diesen Anbieter, nicht an uns. Für diese
Seiten gelten die Datenschutzhinweise des jeweiligen Anbieters.

Die Funktion „Daten einfügen" trägt deine gespeicherten Angaben in die Felder der
**gerade geladenen** Seite ein. Sie tut das nur auf deinen ausdrücklichen Tipp,
und die App zeigt dir vorher an, auf welcher Internet-Adresse du dich befindest.
Führt die Seite auf eine fremde Domain, fragt die App vor dem Einfügen nach.

## Was die App nicht tut

- Kein Nutzerkonto, keine Registrierung, keine Anmeldung
- Keine Analyse-, Statistik- oder Werbe-Bibliotheken
- Kein Tracking, keine Werbe-Kennungen, keine Profilbildung
- Keine Weitergabe von Daten an Dritte
- Keine Übertragung deiner Daten an einen Server des Anbieters — es gibt keinen

## Berechtigungen und wozu sie dienen

| Berechtigung | Wofür |
|---|---|
| Internet | Aktionsliste und Produktbilder laden; Einreichungsseite anzeigen |
| Kamera | Kassenbons und Produkte fotografieren |
| Benachrichtigungen | Erinnerungen an Fristen und Freischaltungen anzeigen |
| Nach dem Neustart starten | Gestellte Erinnerungen nach einem Geräteneustart wiederherstellen |

Für Bilder aus der Galerie nutzt die App die Systemauswahl (Photo Picker). Eine
Berechtigung für den gesamten Speicher wird **nicht** angefordert; die App sieht
nur die Bilder, die du auswählst.

## Texterkennung und Strichcode

Kassenbons und Produktfotos werden auf dem Gerät ausgewertet, um Preis, Datum,
Händler und EAN vorzuschlagen. Die dafür nötigen Modelle sind in der App
enthalten. **Es wird kein Bild an einen Dienst übertragen.**

## Sicherung

Datenbank und Fotos sind von der automatischen Cloud-Sicherung von Android
ausgenommen. Deine Bankverbindung und deine Kassenbons landen dadurch nicht in
einer Sicherung außerhalb deines Geräts.

## Speicherdauer und Löschung

Es gibt keine automatische Löschung; du entscheidest. Einzelne Einreichungen,
Fotos und Konten lassen sich in der App löschen. Deinstallierst du die App,
werden alle lokal gespeicherten Daten mit entfernt.

## Deine Rechte

Da keine Daten an den Anbieter übermittelt werden, liegen bei ihm auch keine
Daten über dich vor, auf die sich Auskunft, Berichtigung oder Löschung beziehen
könnten. Die Kontrolle über die Daten auf deinem Gerät liegt vollständig bei dir.

Für Daten, die du selbst an einen **Aktionsanbieter** übermittelst, wende dich
bitte an diesen Anbieter — er ist dafür der Verantwortliche.

## Kinder

Die App richtet sich nicht an Kinder. Für die Teilnahme an Rabattaktionen ist in
der Regel Volljährigkeit und ein eigenes Bankkonto erforderlich.

## Änderungen

Änderungen dieser Erklärung werden mit neuem Stand in dieser Datei
veröffentlicht.

## Kontakt

**[AUSFÜLLEN: E-Mail-Adresse für Datenschutzanfragen]**
