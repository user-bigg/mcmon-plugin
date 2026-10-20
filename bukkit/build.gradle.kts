plugins {
    id("mcmonitor.java")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))
    compileOnly(libs.spigot.api)
    // Every server ships Netty; 4.1 is the oldest line among them (Paper 1.18 has 4.1.68), so nothing newer is used.
    compileOnly(libs.netty.transport)
    testImplementation(libs.netty.transport)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// Spigot API references JetBrains annotations it does not ship; javac reports that as a classfile warning.
tasks.compileJava {
    options.compilerArgs.add("-Xlint:-classfile")
}

tasks.test {
    systemProperty("io.netty.noUnsafe", "true")
}

tasks.jar {
    enabled = false
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName = "mcmonitor-bukkit"
    archiveClassifier = ""
    destinationDirectory = layout.buildDirectory.dir("parts")
    dependencies {
        exclude(dependency("com.google.code.gson:gson"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
