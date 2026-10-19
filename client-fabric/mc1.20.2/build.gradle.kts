plugins {
    id("net.fabricmc.fabric-loom-remap")
    id("mcmonitor.fabric")
}

dependencies {
    minecraft(libs.minecraft.line1202)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
}
