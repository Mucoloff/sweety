plugins { id("sweety.kotlin-conventions") }

dependencies {
    api(project(":util:serialization"))
    implementation(project(":util:exception"))
}
