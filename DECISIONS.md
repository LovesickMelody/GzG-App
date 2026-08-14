# Entscheidungen

Eine Zeile je Entscheidung: Was, und warum. Neueste unten.

## Umgebung

- **Android-Build laeuft in GitHub Actions, nicht in der Entwicklungsumgebung** — die
  Egress-Policy dieser Session sperrt `dl.google.com`, damit sind Google Maven (AndroidX, AGP)
  und das Android SDK lokal nicht ladbar. Maven Central und die Gradle-Plugin-Portal sind offen.
- **Modulschnitt `:core` (reines Kotlin/JVM) und `:app` (Android)** — dadurch laeuft die
  Geschaeftslogik (Betraege, Kontovorschlag, Duplikatsregel, Summen, CSV) lokal in Sekunden
  gegen Maven Central testbar, ohne Android SDK. Nebenbei haelt es die Regeln frei von
  Framework-Abhaengigkeiten.
- **Kein `plugins { ... apply false }` im Root-Build** — ein solcher Block laedt die
  Plugin-Marker aller Module beim Konfigurieren des Root-Projekts, damit braeuchte auch
  `:core` Google Maven. Jedes Modul deklariert seine Plugins selbst.

## Projekt und Build

- **Package und Application-ID `de.gzgtracker`** — deutsche App ohne Firmenkontext, kein
  `com.example`.
- **AGP 8.7.3 / Kotlin 2.0.21 / KSP / Gradle 8.11.1** — erprobte Kombination fuer compileSdk 35;
  KSP statt kapt, weil Room und Hilt es unterstuetzen und es deutlich schneller baut.
- **Version Catalog (`gradle/libs.versions.toml`)** — alle Versionen an einer Stelle, damit
  Updates nicht ueber mehrere Build-Dateien verstreut sind.
- **Debug-Build mit `applicationIdSuffix ".debug"`** — Debug- und Release-APK lassen sich
  parallel auf demselben Geraet installieren.
- **Release faellt ohne Keystore auf den Debug-Key zurueck** — so faellt aus CI immer eine
  installierbare APK heraus. Sobald die Secrets `KEYSTORE_BASE64` und Co. gesetzt sind, wird
  echt signiert. Es liegt kein Key im Repo.
- **`minifyEnabled` nur im Release** — mit ProGuard-Regeln fuer kotlinx.serialization, Room,
  Retrofit und ML Kit.
- **Kein Core-Library-Desugaring** — `java.time` ist ab API 26 vorhanden, und minSdk ist 26.
- **Backup erlaubt, Bonfotos eingeschlossen** — bei Geraetewechsel sollen Belege mitkommen;
  der CSV-Export-Cache ist ausgenommen.
