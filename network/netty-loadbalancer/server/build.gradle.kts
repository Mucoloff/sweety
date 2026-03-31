group = "dev.sweety.netty.server"

dependencies {
    implementation(project(":util:color"))
    implementation(project(":util:math"))
    implementation(project(":util:logger"))
    implementation(project(":util:thread"))
    implementation(project(":util:time"))
    implementation(project(":network:netty"))
    implementation(project(":network:netty-loadbalancer:packet"))
    implementation(project(":util:system"))

    implementation("com.lmax:disruptor:4.0.0")
}