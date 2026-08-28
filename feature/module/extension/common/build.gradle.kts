plugins { id("sweety.kotlin-conventions") }

dependencies {
    implementation(project(":util:logger"))
    implementation(project(":util:persistence:configuration"))
    implementation(project(":feature:module:extension:api"))
}
