plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// 특정 JDK를 요구하지 않는다(Android Studio의 JDK 21 등 17 이상이면 빌드됨). 결과물은 Java 17용.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
