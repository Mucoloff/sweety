plugins { id("sweety.java-conventions") }

dependencies {
    api(project(":util:persistence:sql4j"))
    api(project(":network:netty"))
    api(project(":network:netty-saas:service"))
    api(project(":util:math"))
    implementation(project(":util:thread"))

    // In-process roundtrip test drives a real local sqlite Database through the gateway.
    testImplementation("org.xerial:sqlite-jdbc:3.50.3.0")
}
