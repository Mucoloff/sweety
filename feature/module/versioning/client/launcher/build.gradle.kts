plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":network:netty"))
    implementation(project(":util:thread"))
    implementation(project(":feature:module:versioning:protocol"))
    implementation(project(":feature:asm-patch:asm-patch-core"))
    implementation(project(":feature:asm-patch:applier"))
}

application {
    mainClass.set("dev.sweety.launcher.MainLauncher")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "dev.sweety.launcher.MainLauncher"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
