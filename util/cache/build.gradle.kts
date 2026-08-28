plugins { id("sweety.kotlin-conventions") }

dependencies {
    api(project(":util:filter"))
    implementation(project(":util:logger"))

    api("com.github.ben-manes.caffeine:caffeine:3.1.8")
    api("it.unimi.dsi:fastutil:8.5.15")
}
