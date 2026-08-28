plugins { id("sweety.java-conventions") }

dependencies {
    api("com.github.hypfvieh:dbus-java-core:5.1.0")
    api("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.0")
    implementation(project(":util:thread"))
    implementation(project(":util:logger"))
    implementation(project(":util:math"))
}
