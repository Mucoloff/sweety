plugins { id("sweety.java-conventions") }

dependencies {
    implementation("com.squareup:javapoet:1.13.0")

    compileOnly("com.google.auto.service:auto-service:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")

    // sql4j API needed so annotations are on classpath when processor tests compile input sources
    testImplementation(project(":util:persistence:sql4j"))

    testImplementation("com.google.testing.compile:compile-testing:0.21.0")
    testImplementation("com.google.truth:truth:1.4.4")
}
