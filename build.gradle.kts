import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("io.freefair.lombok") version "9.0.0" apply false
    kotlin("jvm") version "2.3.20-RC2" apply false
}

group = "dev.sweety"
version = "1.0.0"

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.freefair.lombok")
    apply(plugin = "maven-publish")

    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        mavenLocal()
        maven(url = "https://repo.codemc.io/repository/maven-releases/")
        maven(url = "https://repo.codemc.io/repository/maven-snapshots/")
    }

    dependencies {
        add("implementation", "org.jetbrains:annotations:26.0.2")

        add("implementation", "org.jetbrains.kotlin:kotlin-stdlib")
        add("implementation", "com.google.code.gson:gson:2.13.1")
        add("implementation", "org.yaml:snakeyaml:2.3")
        add("implementation", "org.tomlj:tomlj:1.1.1")

        add("runtimeOnly", "org.joml:joml:1.10.5")
        add("implementation", "org.apache.commons:commons-lang3:3.18.0")
        add("implementation", "org.apache.httpcomponents:httpclient:4.5.14")
        add("implementation", "commons-io:commons-io:2.20.0")

        add("implementation", "org.ow2.asm:asm:9.6")
        add("implementation", "com.google.guava:guava:32.0.1-android")

        add("implementation", "org.slf4j:slf4j-api:2.0.7")
        add("implementation", "it.unimi.dsi:fastutil:8.5.13")
        add("implementation", "io.netty:netty-all:5.0.0.Alpha2")

        add("annotationProcessor", "systems.manifold:manifold-preprocessor:2025.1.27")
        add("implementation", "systems.manifold:manifold-all:2025.1.27")

        add("testImplementation", platform("org.junit:junit-bom:5.10.0"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")

        add("implementation", "com.sun.mail:javax.mail:1.6.2")

        add("implementation", "com.github.ben-manes.caffeine:caffeine:3.1.8")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    afterEvaluate {
        if (plugins.hasPlugin("java")) {
            extensions.configure<PublishingExtension> {
                publications {
                    if (findByName("mavenJava") == null) {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                }
            }
        }
    }
}

tasks.register("buildAll") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("publishAll") {
    dependsOn(subprojects.map { it.tasks.named("publishToMavenLocal") })
}

tasks.register("build-and-publish") {
    dependsOn(
        subprojects.flatMap {
            listOf(
                it.tasks.named("build"),
                it.tasks.named("publishToMavenLocal")
            )
        }
    )
}
