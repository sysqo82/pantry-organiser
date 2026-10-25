import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

val envProperties = Properties()
val envFile = rootProject.file(".env")
if (envFile.exists()) {
    envFile.inputStream().use { envProperties.load(it) }
}

fun getProp(key: String): String? {
    return System.getenv(key)
        ?: envProperties.getProperty(key)
        ?: localProperties.getProperty(key)
        ?: localProperties.getProperty(key.lowercase().replace('_', '.'))
}

val customPocketBaseUrl = localProperties.getProperty("pocketbase.url")
val resolvedTelemetryUrl = getProp("TELEMETRY_URL") ?: "https://telemetry.lockpc.co.uk"
val resolvedTelemetryKey = getProp("TELEMETRY_KEY") ?: ""
val resolvedTelemetryService = getProp("TELEMETRY_SERVICE") ?: "pantry-organiser"

android {
    namespace = "com.pantry.organiser.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            val url = "https://pantry.lockpc.co.uk"
            buildConfigField("String", "POCKETBASE_URL", "\"$url\"")
            buildConfigField("String", "TELEMETRY_URL", "\"$resolvedTelemetryUrl\"")
            buildConfigField("String", "TELEMETRY_KEY", "\"$resolvedTelemetryKey\"")
            buildConfigField("String", "TELEMETRY_SERVICE", "\"$resolvedTelemetryService\"")
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
        }
        debug {
            val url = customPocketBaseUrl ?: "https://dev-pantry.lockpc.co.uk"
            buildConfigField("String", "POCKETBASE_URL", "\"$url\"")
            buildConfigField("String", "TELEMETRY_URL", "\"$resolvedTelemetryUrl\"")
            buildConfigField("String", "TELEMETRY_KEY", "\"$resolvedTelemetryKey\"")
            buildConfigField("String", "TELEMETRY_SERVICE", "\"$resolvedTelemetryService\"")
            buildConfigField("String", "ENVIRONMENT", "\"debug\"")
        }
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)
    api(libs.ktor.client.okhttp)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    
    // Compose for UI elements
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.material3)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
}
