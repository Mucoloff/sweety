plugins {
    id("io.freefair.lombok") version "9.0.0"
}


dependencies {
    implementation(project(":util:color"))
    implementation(project(":util:time"))
    implementation(project(":util:logger"))
    implementation(project(":util:math"))
    //implementation(project(":util:thread"))
    implementation(project(":util:data"))
    implementation(project(":network:netty"))
    implementation(project(":network:netty-loadbalancer:packet"))
    implementation(project(":network:netty-loadbalancer:server"))
    implementation(project(":network:netty-saas:packet"))
    implementation(project(":util:persistence:configuration"))
    //implementation(project(":util:system"))
    implementation("com.lmax:disruptor:4.0.0")
}