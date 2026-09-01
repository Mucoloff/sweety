pluginManagement {
    includeBuild("build-logic/sweety-conventions")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "sweety"

// ── helpers: flat gradle paths ───────────────────────────────────────────────
fun remap(p: ProjectDescriptor, group: String) {
    val relativePath = p.path.trimStart(':').replace(':', '/')
    p.projectDir = if (group.isEmpty()) file(relativePath) else file("$group/$relativePath")
    p.children.forEach { remap(it, group) }
}
fun group(group: String, paths: List<String>) {
    paths.forEach { include(it) }
    paths.map { it.substringBefore(':') }.distinct()
        .forEach { remap(project(":$it"), group) }
}

// ── modules ──────────────────────────────────────────────────────────────────
group("", listOf(
    "feature:event", "feature:event:api", "feature:event:impl",
    "feature:module", "feature:module:extension", "feature:module:extension:api", "feature:module:extension:common", "feature:module:extension:manager",
    "feature:module:extension-versioning", "feature:module:extension-versioning:api", "feature:module:extension-versioning:manager",
    "feature:module:versioning", "feature:module:versioning:protocol", "feature:module:versioning:client-http",
    "feature:module:versioning:client", "feature:module:versioning:client:app", "feature:module:versioning:client:launcher",
    "feature:module:versioning:update-server",
    "feature:transform",
    "feature:asm-patch", "feature:asm-patch:core", "feature:asm-patch:generator", "feature:asm-patch:applier",
    "feature:service", "feature:service:api", "feature:service:impl",

    "network:netty",
    "network:netty-loadbalancer", "network:netty-loadbalancer:backend", "network:netty-loadbalancer:packet", "network:netty-loadbalancer:server",
    "network:netty-saas", "network:netty-saas:hub", "network:netty-saas:service", "network:netty-saas:packet",

    "minecraft:network", "minecraft:version", "minecraft:auth",

    "util:animation", "util:cache", "util:color", "util:exception", "util:file", "util:filter", "util:i18n", "util:logger",
    "util:math", "util:media", "util:serialization", "util:signature", "util:system", "util:thread", "util:time", "util:vector", "util:tree",
    "util:persistence", "util:persistence:configuration", "util:persistence:sql4j",
    "util:persistence:sql4j-processor", "util:persistence:sql4j-rpc", "util:persistence:sql4j-benchmarks",
    "util:persistence:sql4j-integration-tests",

    "tooling:processor", "tooling:ksp", // "tooling:intellij-plugin",
))
