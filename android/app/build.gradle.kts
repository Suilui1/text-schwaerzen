plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.juliusk.textschwaerzen"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.juliusk.textschwaerzen"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // Fester Signaturschlüssel im Projekt: sonst erzeugt jeder Bau-Rechner einen neuen
    // Debug-Schlüssel, und Android verweigert das Update über die alte Fassung
    // („lässt sich nicht über alte Version aktualisieren", 24.11.2026).
    // Signatur kommt aus der Umgebung (GitHub-Secret) — im Repository liegt kein
    // Schlüssel und kein Paßwort mehr (25.11.2026, Vorbereitung des öffentlichen Repos).
    val lagerPfad = System.getenv("SCHWAERZEN_KEYSTORE") ?: ""
    val lagerPass = System.getenv("SCHWAERZEN_STORE_PASS") ?: ""
    val lagerAlias = System.getenv("SCHWAERZEN_KEY_ALIAS") ?: "schwaerzen"
    val hatSignatur = lagerPfad.isNotEmpty() && File(lagerPfad).exists()

    signingConfigs {
        if (hatSignatur) {
            create("fest") {
                storeFile = file(lagerPfad)
                storePassword = lagerPass
                keyAlias = lagerAlias
                keyPassword = lagerPass
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = if (hatSignatur) signingConfigs.getByName("fest") else null
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (hatSignatur) signingConfigs.getByName("fest") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    // Tesseract (tess-two) meldet auf dem Gerät "init fehlgeschlagen" — die Erkennung
    // startet dort nicht (Selbsttest vom 24.11.2026: 0 Wörter). ML Kit bringt sein Modell
    // im Paket mit, arbeitet offline und liefert Wortkästen.
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // Weitere Schriftsysteme (Julius' Wahl „c", 24.11.2026): ML Kit kann nur diese fünf.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.0")
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.0")
    // QR- und Strichcodes: sie enthalten dieselben Daten als Bild und blieben bei
    // "Alles" stehen (Julius' Fahrkarte, 24.11.2026).
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
