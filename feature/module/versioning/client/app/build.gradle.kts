plugins {
    application
}

dependencies {
    implementation(project(":feature:module:extension:api"))
    implementation(project(":feature:module:extension-versioning:api"))
    implementation(project(":feature:module:extension-versioning:manager"))
    implementation(project(":feature:module:versioning:protocol"))
    implementation(project(":util:data"))
}

application {
    mainClass.set("dev.sweety.app.AppMain")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "dev.sweety.app.AppMain"
    }
}

