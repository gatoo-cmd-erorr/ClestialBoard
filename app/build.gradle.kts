plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.clestial.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clestial.keyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Terjemahan di perangkat (ML Kit): model bahasa diunduh manual dari layar Paket Unduhan.
    implementation("com.google.mlkit:translate:17.0.3")

    // OCR Teks di perangkat (ML Kit): mengenali teks soal dari kotak seleksi layar
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
