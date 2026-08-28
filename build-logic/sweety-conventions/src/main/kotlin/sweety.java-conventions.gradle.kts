import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java-library")
    id("maven-publish")
}

version = providers.gradleProperty("version").getOrElse("1.0.0")

val segments = project.path.removePrefix(":").split(":")
group = if (segments.size > 1) "dev.sweety." + segments.dropLast(1).joinToString(".") else "dev.sweety"

extensions.configure<org.gradle.api.plugins.BasePluginExtension> {
    archivesName.set(project.path.removePrefix(":").replace(":", "-"))
}

repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://repo.codemc.io/repository/maven-releases/")
    maven(url = "https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    implementation("org.jetbrains:annotations:26.0.2")

    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.yaml:snakeyaml:2.3")
    implementation("org.tomlj:tomlj:1.1.1")

    runtimeOnly("org.joml:joml:1.10.5")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("commons-io:commons-io:2.20.0")

    implementation("org.ow2.asm:asm:9.6")
    implementation("com.google.guava:guava:32.0.1-android")

    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("it.unimi.dsi:fastutil:8.5.13")
    implementation("io.netty:netty-all:4.1.120.Final")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-suite")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    failOnNoDiscoveredTests = false
}

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
    options.compilerArgs.addAll(
        listOf(
            "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        )
    )
    options.forkOptions.jvmArgs?.addAll(
        listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        )
    )
}

afterEvaluate {
    if (plugins.hasPlugin("java")) {
        extensions.configure<PublishingExtension> {
            publications {
                if (findByName("mavenJava") == null) {
                    create<MavenPublication>("mavenJava") {
                        artifactId = project.path.removePrefix(":").replace(":", "-")
                        from(components["java"])
                    }
                }
            }
        }
    }
}
