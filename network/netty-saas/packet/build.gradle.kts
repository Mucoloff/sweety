plugins {
    id("io.freefair.lombok") version "9.0.0"
}


dependencies {
    implementation(project(":util:color"))
    //implementation(project(":util:math"))
    implementation(project(":util:logger"))
    //implementation(project(":util:thread"))
    implementation(project(":util:data"))
    implementation(project(":network:netty"))
    implementation(project(":network:netty-loadbalancer:packet"))
    implementation(project(":util:persistence:configuration"))
    //implementation(project(":util:system"))
}