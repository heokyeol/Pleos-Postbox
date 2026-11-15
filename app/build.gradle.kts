plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "com.pleospostbox.car"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pleospostbox.car"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // CarUxRestrictionsManager(주행/주차 판단)용. 런타임에는 차량 시스템이 제공한다.
    useLibrary("android.car")

    // release 서명. 키스토어와 비밀번호는 저장소에 두지 않고 환경 변수로만 받는다.
    // tools/build_release.sh가 비밀번호를 입력받아 넘겨 준다.
    val releaseKeystore = System.getenv("PLEOS_KEYSTORE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("PLEOS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PLEOS_KEY_ALIAS") ?: "pleos-postbox"
                keyPassword = System.getenv("PLEOS_KEY_PASSWORD") ?: System.getenv("PLEOS_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
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
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
