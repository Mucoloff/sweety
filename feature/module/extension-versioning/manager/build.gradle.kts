dependencies {
    implementation(project(":feature:module:extension:api"))
    implementation(project(":feature:module:extension:manager"))
    implementation(project(":feature:module:extension-versioning:api"))
    implementation(project(":feature:module:versioning:client-http"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}
