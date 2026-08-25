# Play Console: Data-Safety-Formular

Ausfüllhilfe für *Play Console → App-Inhalte → Datensicherheit*, abgeleitet aus
dem Datenmodell dieser App (`data/local/Entities.kt`), den Berechtigungen im
Manifest und den tatsächlichen Netzwerkzugriffen.

Die entscheidende Unterscheidung im Formular ist **„erhoben" (collected)** gegen
**„weitergegeben" (shared)**. Google definiert „erhoben" als *„die App überträgt
Daten von deinem Gerät"* — nicht als „die App speichert etwas". Weil GZG-Tracker
alles ausschließlich lokal ablegt und keinen Server hat, lautet die Antwort für
jede Kategorie **nein**.

> Das ist keine Auslegungsfrage, sondern folgt daraus, dass es keinen Endpunkt
> gibt, an den etwas ginge: Der einzige ausgehende Abruf der App ist die
> öffentliche `actions.json`, und der sendet nichts über den Nutzer.

## Die Antworten

| Frage im Formular | Antwort | Begründung |
|---|---|---|
| Erhebt oder teilt deine App Nutzerdaten? | **Nein** | Kein Server, kein Konto, keine Übertragung |
| Werden Daten bei der Übertragung verschlüsselt? | entfällt | Es werden keine Nutzerdaten übertragen |
| Können Nutzer die Löschung ihrer Daten beantragen? | entfällt | Löschung erfolgt in der App bzw. durch Deinstallation |

### Wichtig zur Abgrenzung

Drei Dinge könnten fälschlich als Erhebung gewertet werden — sie sind es nicht:

1. **Die Einreichungsseite im eingebetteten Browser.** Was der Nutzer dort
   absendet, geht an den Aktionsanbieter. Das ist eine Übermittlung durch den
   Nutzer an einen Dritten, nicht durch die App an ihren Anbieter. Google zählt
   Daten, die der Nutzer sichtbar selbst in einer angezeigten Website eingibt,
   nicht als Erhebung durch die App.
2. **Der Abruf der Aktionsliste.** Rein lesend, ohne Kennung, ohne Parameter.
3. **Texterkennung auf Bons.** Läuft vollständig auf dem Gerät; die Modelle sind
   in der App enthalten.

Sollte sich daran je etwas ändern — etwa eine Synchronisierung, eine
Fehlerberichterstattung oder eine Analysebibliothek — **muss dieses Formular
neu beantwortet werden.** Eine falsche Angabe ist einer der häufigsten Gründe
für eine Sperrung.

## Weitere Angaben, die Play verlangt

| Punkt | Wert |
|---|---|
| Datenschutzerklärung (URL) | muss öffentlich erreichbar sein — siehe unten |
| Zielgruppe | Erwachsene; die App richtet sich nicht an Kinder |
| Werbung | keine |
| Käufe in der App | keine |
| Finanzdaten | werden verarbeitet, aber **nur lokal** — siehe Abgrenzung oben |

## Die Datenschutzerklärung erreichbar machen

Play verlangt eine öffentliche URL, keine Datei im Repository. `PRIVACY.md` lässt
sich mit GitHub Pages veröffentlichen:

*Settings → Pages → Source: „Deploy from a branch" → Branch `main`, Ordner `/`*

Danach ist der Text unter
`https://lovesickmelody.github.io/GzG-App/PRIVACY` erreichbar. Diese Adresse
gehört ins Formular und zusätzlich in den Play-Store-Eintrag.

## Vor dem ersten Upload noch offen

- [ ] Verantwortlichen und Kontaktadresse in `PRIVACY.md` eintragen
      (zwei Stellen mit **[AUSFÜLLEN]**)
- [ ] Datenschutzerklärung veröffentlichen und die URL eintragen
- [ ] Eigenen Keystore anlegen und die vier Secrets hinterlegen
      (README, „Für den Play Store signieren")
- [ ] Klären, ob die Aktionsdaten aus den Portalen kommerziell weitergegeben
      werden dürfen — das ist die offene Rechtsfrage, nicht eine Formularfrage
