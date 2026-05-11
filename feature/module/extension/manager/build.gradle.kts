dependencies {
    implementation(project(":util:exception"))
    implementation(project(":util:logger"))
    implementation(project(":feature:module:extension:api"))
    implementation(project(":feature:module:extension:common"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}