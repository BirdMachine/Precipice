import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties().apply {
    val source = rootProject.file(".signing/signing.properties")
    if (source.isFile) source.inputStream().use { load(it) }
}
fun signingValue(name: String): String? = System.getenv(name) ?: signingProperties.getProperty(name)
val fixedKeystore = signingValue("PRECIPICE_KEYSTORE")

android {
    namespace = "dev.birdmachine.precipice"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.birdmachine.precipice"
        minSdk = 24
        targetSdk = 37
        versionCode = providers.gradleProperty("precipiceVersionCode").orElse((System.currentTimeMillis() / 60_000L).toString()).get().toInt()
        versionName = "0.2.0"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (fixedKeystore != null) {
            create("fixedTest") {
                storeFile = rootProject.file(fixedKeystore)
                storePassword = signingValue("PRECIPICE_STORE_PASSWORD")
                    ?: error("Persistent test signer: store password missing")
                keyAlias = signingValue("PRECIPICE_KEY_ALIAS") ?: "precipice-test"
                keyPassword = signingValue("PRECIPICE_KEY_PASSWORD") ?: storePassword
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Never publish an APK with a fresh auto-generated debug signer.
            signingConfig = if (fixedKeystore != null) signingConfigs.getByName("fixedTest") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")

    implementation("com.github.BuildItCode:LiquidGlass:0.2.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
