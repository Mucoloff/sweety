plugins { id("sweety.java-conventions") }

dependencies {
    implementation(project(":util:math"))
    implementation(project(":util:thread"))

    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.4.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.postgresql:postgresql:42.7.5")

    annotationProcessor(project(":util:persistence:sql4j-processor"))
    testAnnotationProcessor(project(":util:persistence:sql4j-processor"))
}
