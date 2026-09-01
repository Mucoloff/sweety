import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("sweety.kotlin-conventions")
    application
    id("com.gradleup.shadow") version "9.3.1"
}

application {
    mainClass.set("dev.sweety.versioning.server.MainServer")
    applicationDefaultJvmArgs = listOf("--enable-preview")
}

tasks.withType<ShadowJar>().configureEach {
    archiveClassifier.set("all")
    manifest { attributes["Main-Class"] = "dev.sweety.versioning.server.MainServer" }
}

dependencies {
    implementation(project(":util:math"))
    implementation(project(":util:exception"))
    implementation(project(":util:signature"))
    implementation(project(":util:thread"))
    implementation(project(":util:logger"))
    implementation(project(":util:time"))
    implementation(project(":network:netty"))
    implementation(project(":network:netty-saas:service"))
    implementation(project(":feature:module:versioning:protocol"))
    implementation(project(":feature:asm-patch:core"))
    implementation(project(":feature:asm-patch:generator"))
    testImplementation(project(":feature:module:versioning:client-http"))
}
