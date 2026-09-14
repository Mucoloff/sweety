plugins { id("sweety.java-conventions") }

dependencies {
    api(project(":util:math"))
    testImplementation(project(":network:netty"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED")
}
