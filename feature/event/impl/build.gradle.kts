plugins { id("sweety.ksp-conventions") }

dependencies {
    implementation(project(":feature:event:api"))
    implementation(project(":tooling:processor"))
    ksp(project(":tooling:ksp"))
    implementation(project(":util:thread"))
}
