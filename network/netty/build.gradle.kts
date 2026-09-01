plugins { id("sweety.java-conventions") }

dependencies {
    implementation(project(":util:math"))
    implementation(project(":util:file"))
    implementation(project(":util:color"))
    implementation(project(":util:exception"))
    implementation(project(":util:logger"))
    implementation(project(":util:time"))
    implementation(project(":util:thread"))
    testImplementation(project(":util:cache"))
}
