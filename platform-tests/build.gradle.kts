import java.time.Duration

plugins {
    id("mcmonitor.java")
}

evaluationDependsOn(":plugin")

dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":fake-site"))
    testImplementation(libs.gson)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.testcontainers)
    testRuntimeOnly(libs.junit.launcher)
}

val pluginJar = project(":plugin").tasks.named<Jar>("pluginJar")

tasks.test {
    onlyIf("platform tests run with -Pplatform") { project.hasProperty("platform") }
    inputs.files(pluginJar)
    systemProperty(
        "mcmonitor.pluginJar",
        pluginJar
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
