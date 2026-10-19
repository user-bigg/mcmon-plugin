plugins {
    id("mcmonitor.java")
}

// The mod runs on Minecraft 1.14 and Java 8, where the game ships Gson 2.8.0.
tasks.compileJava {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

dependencies {
    compileOnly(libs.gson.minecraft)
    testImplementation(libs.gson.minecraft)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
