plugins { id("sweety.kotlin-conventions") }

dependencies {
    implementation(project(":util:logger"))
    implementation(project(":util:file"))
    implementation(project(":util:system"))
    implementation(project(":util:persistence:configuration"))
    implementation(project(":util:thread"))
}