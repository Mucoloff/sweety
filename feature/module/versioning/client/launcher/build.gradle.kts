import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("sweety.java-conventions")
    application
    id("com.gradleup.shadow") version "9.3.1"
}

application {
    mainClass.set("dev.sweety.launcher.LauncherMain")
    applicationDefaultJvmArgs = listOf("--enable-preview")
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("all")
    manifest { attributes["Main-Class"] = "dev.sweety.launcher.LauncherMain" }
}

dependencies {
    implementation(project(":network:netty"))
    implementation(project(":util:logger"))
    implementation(project(":util:math"))
    implementation(project(":util:thread"))
    implementation(project(":feature:module:versioning:protocol"))
    implementation(project(":feature:asm-patch:core"))
    implementation(project(":feature:asm-patch:applier"))
}
