dependencies {
    implementation(project(":feature:asm-patch:asm-patch-core"))
    implementation("io.github.java-diff-utils:java-diff-utils:4.15")

    testImplementation(project(":feature:asm-patch:applier"))
}