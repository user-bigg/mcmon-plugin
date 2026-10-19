plugins {
    id("mcmonitor.java")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(libs.gson)
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "ru.mcmon.plugin.fakesite.FakeSiteMain")
    }
}

tasks.shadowJar {
    archiveFileName = "fake-site-all.jar"
}
