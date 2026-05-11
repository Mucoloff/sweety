dependencies {
    implementation(project(":feature:event:api"))

    val processor = project(":feature:event:event-processor")

    implementation(processor)
    annotationProcessor(processor)

    implementation(project(":util:thread"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}