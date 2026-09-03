plugins {
    id("sweety.java-conventions")
}

dependencies {
    api(project(":util:logger"))
    api(project(":util:file"))
    api(project(":util:math"))
    api("ac.ecstacy:Obfuscator")
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
    implementation("org.jetbrains:annotations:26.0.2")
}
