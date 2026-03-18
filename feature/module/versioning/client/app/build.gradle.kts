plugins {
    application
}

dependencies {
}

application {
    mainClass.set("dev.sweety.app.AppMain")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "dev.sweety.app.AppMain"
    }
}

