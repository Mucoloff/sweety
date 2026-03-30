plugins {
    id("io.freefair.lombok") version "9.0.0" apply false
    kotlin("jvm") version "2.3.20-RC2" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.freefair.lombok")
    apply(plugin = "maven-publish")
}