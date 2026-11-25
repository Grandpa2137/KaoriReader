plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.io.FileInputStream
import java.util.Properties

// Klucz podpisujÄ…cy trzymamy POZA repo (android/keystore.properties jest w .gitignore).
// Bez tego pliku build dziaĹ‚a dalej â€” release wychodzi wtedy niepodpisany.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
}

android {
    namespace = "app.kaori.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.kaori.reader"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "0.2.8"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.containsKey("storeFile")) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // Magazyny danych logujÄ… przez android.util.Log â€” w testach JVM ma to zwracaÄ‡ 0
        // zamiast rzucaÄ‡ "Method w in android.util.Log not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Testy czystej logiki (spoiler guard, dzielenie tekstu) â€” bez androida i bez sieci.
    testImplementation("junit:junit:4.13.2")
    // Prawdziwy org.json â€” w unit testach android.jar to atrapa i put() zwraca null.
    testImplementation("org.json:json:20240303")
}
