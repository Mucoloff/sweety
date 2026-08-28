plugins { id("sweety.kotlin-conventions") }

dependencies {
    api(project(":util:exception"))
    api(project(":util:math"))
    api(project(":network:netty"))
    implementation(project(":feature:asm-patch:core"))
    implementation(project(":util:logger"))
}
