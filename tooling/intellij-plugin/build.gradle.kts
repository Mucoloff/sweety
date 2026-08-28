plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "dev.luce"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.3.1")
        bundledPlugin("com.intellij.java")
    }
}

java {
    toolchain { languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21)) }
}

tasks.compileJava {
    options.release.set(21)
    options.compilerArgs.add("-Xlint:deprecation")
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = provider { null }
        }
    }
}
