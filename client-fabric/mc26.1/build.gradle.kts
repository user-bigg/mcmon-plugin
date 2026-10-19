plugins {
    id("net.fabricmc.fabric-loom")
    id("mcmonitor.fabric")
}

dependencies {
    minecraft(libs.minecraft.line261)
    implementation(libs.fabric.loader)
}

tasks.compileJava {
    options.release = 25
}
