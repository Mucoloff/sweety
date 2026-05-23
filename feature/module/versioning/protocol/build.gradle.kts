dependencies {
    api(project(":util:exception"))
    api(project(":util:data"))
    api(project(":network:netty"))
    implementation(project(":util:math"))
    implementation(project(":feature:asm-patch:asm-patch-core"))
    implementation("com.google.code.gson:gson:2.11.0")
}