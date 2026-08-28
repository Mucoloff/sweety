plugins { id("sweety.java-conventions") }

group = "dev.sweety.versioning"

dependencies {
    implementation(project(":util:i18n"))
    implementation(project(":feature:module:extension:api"))
    implementation(project(":feature:module:extension:manager"))
    implementation(project(":feature:module:extension-versioning:api"))
    implementation(project(":feature:module:versioning:client-http"))
}
