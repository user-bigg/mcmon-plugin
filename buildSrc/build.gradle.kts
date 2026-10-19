plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/") {
        content {
            includeGroupAndSubgroups("net.fabricmc")
        }
    }
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.10.4")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:5.1.1")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
    implementation("net.fabricmc:fabric-loom:1.19.1")
}
