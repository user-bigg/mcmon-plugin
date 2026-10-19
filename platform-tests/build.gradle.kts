import java.time.Duration

plugins {
    id("mcmonitor.java")
}

evaluationDependsOn(":bukkit")
evaluationDependsOn(":proxy")

dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":fake-site"))
    testImplementation(libs.gson)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers)
    testRuntimeOnly(libs.junit.launcher)
}

val bukkitJar = project(":bukkit").tasks.named<Jar>("shadowJar")
val proxyJar = project(":proxy").tasks.named<Jar>("shadowJar")

tasks.test {
    onlyIf("platform tests run with -Pplatform") { project.hasProperty("platform") }
    inputs.files(bukkitJar, proxyJar)
    systemProperty(
        "mcmonitor.bukkitJar",
        bukkitJar
            .get()
            .archiveFile
            .get()
            .asFile.absolutePath,
    )
    systemProperty(
        "mcmonitor.proxyJar",
        proxyJar
            .get()
            .archiveFile
            .get()
            .asFile.absolutePath,
    )
    systemProperty("mcmonitor.platform", project.findProperty("platform")?.toString() ?: "all")
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    timeout = Duration.ofMinutes(40)
}
