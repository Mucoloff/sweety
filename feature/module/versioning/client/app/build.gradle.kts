plugins {
    id("sweety.java-conventions")
    application
}

application {
    mainClass.set("dev.sweety.app.AppMain")
    applicationDefaultJvmArgs = listOf("--enable-preview")
}

distributions.all {
    contents { duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE }
}

dependencies {
    implementation(project(":feature:module:extension:api"))
    implementation(project(":feature:module:extension-versioning:api"))
    implementation(project(":feature:module:extension-versioning:manager"))
    implementation(project(":feature:module:versioning:protocol"))
    implementation(project(":util:math"))
}
