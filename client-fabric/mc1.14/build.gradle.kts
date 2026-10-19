plugins {
    id("net.fabricmc.fabric-loom-remap")
    id("mcmonitor.fabric")
}

// Mojang mappings start at 1.14.4; the intermediary names this jar runs on are the same from 1.14 to 1.21.11.
dependencies {
    minecraft(libs.minecraft.line114)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
}

tasks.compileJava {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}
