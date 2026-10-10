import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val customPocketBaseUrl = localProperties.getProperty("pocketbase.url")

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
    }

    buildTypes {
        release {
            val url = customPocketBaseUrl ?: "https://pantry.lockpc.co.uk"
            buildConfigField("String", "POCKETBASE_URL", "\"$url\"")
        }
        debug {
            val url = customPocketBaseUrl ?: "https://dev-pantry.lockpc.co.uk"
            buildConfigField("String", "POCKETBASE_URL", "\"$url\"")
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

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
}
