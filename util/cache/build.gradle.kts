plugins { id("sweety.kotlin-conventions") }

dependencies {
    implementation(project(":util:logger"))
    implementation(project(":util:math"))

    api("com.github.ben-manes.caffeine:caffeine:3.1.8")
    api("it.unimi.dsi:fastutil:8.5.15")
}
