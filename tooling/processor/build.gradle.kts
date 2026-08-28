plugins { id("sweety.java-conventions") }

dependencies {
    implementation(project(":feature:event:api"))
    implementation(project(":network:netty"))
    implementation(project(":util:math"))
    implementation("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
    implementation("com.squareup:javapoet:1.13.0")

    testAnnotationProcessor(project(":tooling:processor"))
}
