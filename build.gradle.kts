// Bewusst ohne `plugins { ... apply false }`-Block.
//
// Ein Root-Plugin-Block laedt die Plugin-Marker aller Module (inkl. Android Gradle
// Plugin) schon beim Konfigurieren des Root-Projekts. Dadurch braeuchte auch
// `:core` — ein reines Kotlin/JVM-Modul ohne Android-Bezug — Zugriff auf Google
// Maven. So bleibt `./gradlew :core:test --configure-on-demand` in Umgebungen
// lauffaehig, die nur Maven Central erreichen.
//
// Jedes Modul deklariert seine Plugins selbst in der eigenen build.gradle.kts.

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
