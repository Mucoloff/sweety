plugins {
    id("sweety.java-conventions")
}

dependencies {
    implementation(project(":util:math"))
    implementation(project(":util:color"))
    implementation(project(":util:logger"))
    implementation(project(":util:thread"))
    implementation(project(":util:time"))
    implementation(project(":network:netty"))
    implementation(project(":util:system"))
}
