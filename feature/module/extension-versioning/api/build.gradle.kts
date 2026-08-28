plugins { id("sweety.java-conventions") }

group = "dev.sweety.versioning"

dependencies {
    implementation(project(":util:logger"))
    implementation(project(":feature:module:extension:api"))
    implementation(project(":feature:module:versioning:protocol"))
}

