plugins {
    id("mcmonitor.java")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    compileOnly(libs.bungeecord.api)
}

// Velocity 4 publishes its API for Java 25 only; the plugin still ships Java 17 bytecode and runs on Velocity 3.5 too.
listOf(configurations.compileClasspath, configurations.annotationProcessor).forEach { configuration ->
    configuration.configure {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
        }
    }
}

tasks.compileJava {
    options.compilerArgs.add("-Xlint:-processing")
}

tasks.jar {
    enabled = false
}

val velocity3Classpath = configurations.create("velocity3Classpath")

dependencies {
    velocity3Classpath(libs.velocity3.api)
    velocity3Classpath(project(":core"))
}

val checkVelocity3Api =
    tasks.register<JavaCompile>("checkVelocity3Api") {
        description = "Compiles the Velocity module against the Velocity 3 API to catch calls missing there."
        source = fileTree("src/main/java/ru/mcmon/plugin/velocity") + fileTree(generatedSources)
        classpath = velocity3Classpath
        destinationDirectory = layout.buildDirectory.dir("classes/velocity3")
        javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(25) }
        options.release = 17
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(
            listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-processing", "-Xlint:-classfile", "-proc:none", "-Werror"),
        )
        dependsOn(buildConstants)
    }

tasks.check {
    dependsOn(checkVelocity3Api)
}

val generatedSources = layout.buildDirectory.dir("generated/sources/buildConstants/java")

val buildConstants =
    tasks.register("buildConstants") {
        val version = project.version.toString()
        val outputDir = generatedSources
        inputs.property("version", version)
        outputs.dir(outputDir)
        doLast {
            val file = outputDir.get().file("ru/mcmon/plugin/velocity/BuildConstants.java").asFile
            file.parentFile.mkdirs()
            file.writeText(
                """
                package ru.mcmon.plugin.velocity;

                final class BuildConstants {

                    static final String VERSION = "$version";

                    private BuildConstants() {}
                }
                """.trimIndent() + "\n",
            )
        }
    }

sourceSets.main {
    java.srcDir(buildConstants)
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("bungee.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName = "mcmonitor-proxy"
    archiveClassifier = ""
    dependencies {
        exclude(dependency("com.google.code.gson:gson"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
