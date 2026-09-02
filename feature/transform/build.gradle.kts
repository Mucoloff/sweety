plugins {
    id("sweety.java-conventions")
}

group = "dev.sweety.feature"
version = "1.0.0"

dependencies {
    api("dev.sweety.util:logger")
    api("dev.sweety.util:file")
    api("dev.sweety.util:math")
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
    implementation("org.jetbrains:annotations:26.0.2")
}
