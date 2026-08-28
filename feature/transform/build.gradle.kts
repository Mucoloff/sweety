import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("sweety.java-conventions")
    id("com.gradleup.shadow") version "9.3.1"
}

val shadowInclude: Configuration by configurations.creating
configurations.compileOnly { extendsFrom(shadowInclude) }

dependencies {
    implementation(project(":util:logger"))
    implementation(project(":util:signature"))

    shadowInclude("org.ow2.asm:asm:9.6")
    shadowInclude("org.ow2.asm:asm-commons:9.6")
    shadowInclude("org.ow2.asm:asm-tree:9.6")
    shadowInclude("org.ow2.asm:asm-analysis:9.6")
    shadowInclude("org.ow2.asm:asm-util:9.6")

    // ASM is compileOnly (shadowed) — the PoC test runs the transformer, so needs ASM at test runtime.
    testImplementation("org.ow2.asm:asm:9.6")
    testImplementation("org.ow2.asm:asm-commons:9.6")
    testImplementation("org.ow2.asm:asm-tree:9.6")
    testImplementation("org.ow2.asm:asm-analysis:9.6")
    testImplementation("org.ow2.asm:asm-util:9.6")
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("all")
    configurations = listOf(project.configurations.runtimeClasspath.get(), shadowInclude)
}
