plugins {
    id("sweety.java-conventions")
    id("me.champeau.jmh") version "0.7.2"
}

// JDK Flight Recorder: either
//   ./gradlew :util:persistence:sql4j-benchmarks:jmh -Pjfr
//   ./gradlew :util:persistence:sql4j-benchmarks:jmhJfr
// Recording: build/sql4j-jmh.jfr (open in JDK Mission Control / `jfr print`). Prefer
// -Djmh.fork=1 so forks do not overwrite the same file.
val jfrRequested =
    project.hasProperty("jfr") ||
        gradle.startParameter.taskNames.any { it.contains("jmhJfr") }

dependencies {
    // sql4j core + RPC gateway available in the jmh source set (RPC-path benchmarks drive
    // RemoteSqlConnection -> SqlGatewayHandler in-process, no real netty transport needed).
    jmh(project(":util:persistence:sql4j"))
    jmh(project(":util:persistence:sql4j-rpc"))

    // H2 in-memory DB — no container needed
    jmh("com.h2database:h2:2.3.232")
    jmh("org.xerial:sqlite-jdbc:3.50.3.0")
    // MariaDbPsCacheBenchmark needs these directly — sql4j declares them `implementation`
    // (not `api`), so they don't cross into this module's `jmh` classpath transitively.
    jmh("com.zaxxer:HikariCP:6.2.1")
    jmh("org.mariadb.jdbc:mariadb-java-client:3.5.1")

    // JMH runtime (the plugin adds jmh-core and annprocess automatically,
    // but declaring them here keeps IDEs happy)
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    // sql4j annotation processor — generates *Table mirror classes used in benchmarks
    jmhAnnotationProcessor(project(":util:persistence:sql4j-processor"))
}

jmh {
    // Defaults tuned for fast local runs; increase on a dedicated benchmark machine:
    //   ./gradlew :util:persistence:sql4j-benchmarks:jmh -Djmh.fork=3 -Djmh.wi=5 -Djmh.i=10
    fork.set(Integer.getInteger("jmh.fork", 1))
    warmupIterations.set(Integer.getInteger("jmh.wi", 3))
    iterations.set(Integer.getInteger("jmh.i", 5))
    timeUnit.set("ms")
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("jmh-result.json"))

    if (jfrRequested) {
        val jfrFile = layout.buildDirectory.file("sql4j-jmh.jfr").get().asFile.absolutePath
        // disk=true + dumponexit=true AND an explicit maxage so the recording is flushed to
        // disk continuously — survives JMH's forced VM kill at the end of a fork.
        jvmArgsAppend.set(
            listOf(
                "-XX:+FlightRecorder",
                "-XX:StartFlightRecording=disk=true,dumponexit=true,filename=$jfrFile,maxage=1d",
            ),
        )
    }
}

tasks.register("jmhJfr") {
    group = "verification"
    description = "Runs JMH with JDK Flight Recorder (build/sql4j-jmh.jfr). Prefer -Djmh.fork=1."
    dependsOn(tasks.named("jmh"))
}
