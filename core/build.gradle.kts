plugins {
    id("java")
    kotlin("jvm") version "2.3.20-RC2"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}