plugins { id("sweety.java-conventions") }

dependencies {
    api(project(":feature:module:versioning:protocol"))
    implementation(project(":util:math"))
    implementation(project(":network:netty"))
}

