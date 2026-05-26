plugins {
    kotlin("jvm") version "2.3.20"
    id("io.freefair.lombok") version "9.0.0"
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}