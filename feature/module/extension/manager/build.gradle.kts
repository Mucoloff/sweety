plugins { id("sweety.java-conventions") }

dependencies {
    implementation(project(":util:exception"))
    implementation(project(":util:logger"))
    implementation(project(":util:i18n"))
    implementation(project(":feature:module:extension:api"))
    implementation(project(":feature:module:extension:common"))
}
