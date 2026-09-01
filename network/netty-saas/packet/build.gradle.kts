plugins {
    id("sweety.java-conventions")
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    implementation(project(":util:color"))
    implementation(project(":util:logger"))
    implementation(project(":util:math"))
    implementation(project(":network:netty"))
    implementation(project(":network:netty-loadbalancer:packet"))
    implementation(project(":util:persistence:configuration"))
}
