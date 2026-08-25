import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release-Signing ist optional. Liegt eine keystore.properties vor (lokal, nicht im
// Repo) oder sind die KEYSTORE_*-Variablen gesetzt (GitHub Secrets), wird damit
// signiert. Sonst faellt der Release-Build auf den Debug-Key zurueck, damit aus CI
// immer eine installierbare APK herausfaellt. Details in der README.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val releaseStorePath = signingValue("storeFile", "KEYSTORE_PATH")
val hasReleaseKeystore = releaseStorePath != null && file(releaseStorePath).exists()

// Notbremse gegen den teuersten Fehler dieses Aufbaus: einen Release-Build mit
// dem Debug-Key zu veroeffentlichen. Der laesst sich installieren und sieht
// echt aus, taugt aber nicht fuer den Play Store — und wer ihn installiert hat,
// kann spaeter **kein** Update auf die richtig signierte Fassung bekommen, weil
// Android eine andere Signatur als andere App behandelt.
//
// `-PsigningRequired` macht daraus einen Baufehler statt einer stillen
// Ersatzsignatur. Die Veroeffentlichung setzt es; lokal bleibt der bequeme Weg.
val signingRequired = providers.gradleProperty("signingRequired").isPresent
if (signingRequired && !hasReleaseKeystore) {
    throw GradleException(
        "Release-Signierung verlangt, aber kein Keystore gefunden.\n" +
            "Erwartet wird KEYSTORE_PATH (plus KEYSTORE_PASSWORD, KEY_ALIAS, " +
            "KEY_PASSWORD) oder eine keystore.properties im Projektstamm.\n" +
            "Wie man beides anlegt, steht in der README unter " +
            "\"Für den Play Store signieren\".",
    )
}

// Play verlangt fuer jeden Upload einen hoeheren versionCode. Aus dem Tag
// abgeleitet zu werden ist verlaesslicher, als ihn von Hand hochzuzaehlen und
// es einmal zu vergessen.
val versionCodeAusTag = providers.gradleProperty("versionCode").orNull?.toIntOrNull()
val versionNameAusTag = providers.gradleProperty("versionName").orNull

android {
    namespace = "de.gzgtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.gzgtracker"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeAusTag ?: 1
        versionName = versionNameAusTag ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // Startwert fuer den Aktions-Feed. In den Einstellungen ueberschreibbar,
        // damit der Feed auch aus einem anderen Repo kommen kann.
        buildConfigField(
            "String",
            "ACTIONS_FEED_URL",
            "\"https://raw.githubusercontent.com/LovesickMelody/GzG-App/main/data/actions.json\"",
        )
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }
}

// Room legt das Schema als JSON ab. Damit sind spaetere Migrationen nachvollziehbar
// und Room kann sie beim Bauen gegenpruefen.
//
// Bewusst ueber das Room-Plugin statt ueber `ksp { arg("room.schemaLocation", ...) }`:
// Das KSP-Argument gilt fuer alle Varianten gleichzeitig, also schreiben
// kspDebugKotlin und kspReleaseKotlin bei parallelen Builds in dieselbe Datei. Der
// eine leert sie, waehrend der andere sie liest — Room bricht dann mit
// "Empty schema file" ab, und zwar je nach Timing mal so, mal so. Das Plugin gibt
// jeder Variante ein eigenes Unterverzeichnis und meldet es als Task-Ausgabe an.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // CameraX ist mit dem Scan-Bildschirm entfallen: Fotos macht die Kamera-App
    // des Geraets. Der Barcode-Leser ist zurueck, aber ohne Live-Sucher — er
    // liest den Strichcode aus dem fertigen Produktfoto, das ohnehin gemacht
    // wird. Das kostet eine Bibliothek und keinen zusaetzlichen Handgriff.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.exifinterface)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
