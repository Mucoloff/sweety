plugins { id("sweety.kotlin-conventions") }

dependencies {
    api(project(":util:serialization"))
    implementation(project(":util:exception"))

    compileOnly("systems.manifold:manifold-ext:2025.1.31")
    annotationProcessor("systems.manifold:manifold-ext:2025.1.31")
    testAnnotationProcessor("systems.manifold:manifold-ext:2025.1.31")
    implementation("systems.manifold:manifold-ext-rt:2025.1.31")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xplugin:Manifold")
}
