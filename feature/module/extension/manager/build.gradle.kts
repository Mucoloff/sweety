dependencies {
    implementation(project(":util:exception"))
    implementation(project(":util:logger"))
    implementation(project(":feature:module:extension:api"))
    implementation(project(":feature:module:extension:common"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}