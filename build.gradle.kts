group = "dev.sweety"
version = "1.0.0"

tasks.register("buildAll") {
    group = "sweety"
    description = "Build every subproject that has a build task."
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("build") })
}

tasks.register("publishAll") {
    group = "sweety"
    description = "Publish every subproject to mavenLocal."
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("publishToMavenLocal") })
}
