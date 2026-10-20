plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "mcmonitor"

include("core", "fake-site", "bukkit", "proxy", "plugin", "platform-tests")
include("client-fabric:common", "client-fabric:mc1.14", "client-fabric:mc1.20.2", "client-fabric:mc26.1")

dependencyResolutionManagement {
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/") {
            content {
                includeGroupAndSubgroups("com.velocitypowered")
                includeGroupAndSubgroups("io.papermc")
                includeGroupAndSubgroups("net.kyori")
                includeGroupAndSubgroups("com.mojang")
                includeGroupAndSubgroups("net.md-5")
            }
        }
        maven("https://hub.spigotmc.org/nexus/content/groups/public/") {
            content {
                includeGroup("org.spigotmc")
                includeGroupAndSubgroups("net.md-5")
            }
        }
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            content {
                includeGroupAndSubgroups("net.md-5")
            }
        }
        mavenCentral()
    }
}
