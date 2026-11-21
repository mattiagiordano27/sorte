import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// cartella di compilazione ignorata da iCloud (il Desktop è sincronizzato e duplicava i file)
layout.buildDirectory.set(file("build.nosync"))

// firma di release: chiave in ../firma (fuori dal repository); senza, si firma con la chiave di debug
val keyProps = Properties().apply {
    val f = rootProject.file("firma/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "mattiagiordano.sorte"
    compileSdk = 35

    defaultConfig {
        applicationId = "mattiagiordano.sorte"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.27"
    }

    signingConfigs {
        if (keyProps.isNotEmpty()) create("release") {
            storeFile = file(keyProps.getProperty("storeFile"))
            storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias")
            keyPassword = keyProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keyProps.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    // Glyph Matrix SDK di Nothing: scaricalo da
    // https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit e mettilo in app/libs/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}
