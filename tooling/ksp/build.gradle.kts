plugins { id("sweety.kotlin-conventions") }

dependencies {
    implementation(project(":tooling:processor"))
    implementation(project(":feature:event:api"))
    implementation(project(":network:netty"))
    implementation(project(":util:math"))
    implementation(project(":util:persistence:sql4j"))

    compileOnly("com.google.devtools.ksp:symbol-processing-api:2.3.8")

    implementation("com.squareup:javapoet:1.13.0")
}
