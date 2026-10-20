plugins {
    base
}

evaluationDependsOn(":bukkit")
evaluationDependsOn(":proxy")

val bukkitJar = project(":bukkit").tasks.named<Jar>("shadowJar")
val proxyJar = project(":proxy").tasks.named<Jar>("shadowJar")

// One file for every platform: Bukkit reads plugin.yml, BungeeCord bungee.yml, Velocity velocity-plugin.json.
// Both parts carry the same core classes and messages, so a duplicate entry is the same file.
val pluginJar =
    tasks.register<Jar>("pluginJar") {
        description = "Merges the Bukkit and proxy jars into one plugin jar."
        archiveBaseName = "mcmonitor-plugin"
        destinationDirectory = layout.buildDirectory.dir("libs")
        from(bukkitJar.map { zipTree(it.archiveFile) })
        from(proxyJar.map { zipTree(it.archiveFile) })
        exclude("META-INF/MANIFEST.MF")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

tasks.assemble {
    dependsOn(pluginJar)
}
