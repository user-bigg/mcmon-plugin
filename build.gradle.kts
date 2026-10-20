allprojects {
    group = "ru.mcmon.plugin"
    version = "0.2.1"

    // Release jars are rebuilt from the tag on GitHub and must match a local build byte for byte.
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        filePermissions { unix("rw-r--r--") }
        dirPermissions { unix("rwxr-xr-x") }
    }
}
