dependencies {
    implementation(project(":util:exception"))
    implementation(project(":util:logger"))
    implementation(project(":feature:module:extension:common"))
    implementation(project(":feature:module:extension:api"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}