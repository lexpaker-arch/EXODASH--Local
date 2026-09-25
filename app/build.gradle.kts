import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Le secrets.properties
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.exodash"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.exodash"
        minSdk = 24
        targetSdk = 34
        versionCode = 17
        versionName = "3.0.2"

        ndk {
            abiFilters.clear()
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // Chave Groq embutida no BuildConfig
        val groqKey = secrets.getProperty("GROQ_API_KEY", "")
        buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    // OBD USB direto
    implementation("com.github.mik3y:usb-serial-for-android:3.10.0")
    // Dependências locais (baixadas manualmente)
}
