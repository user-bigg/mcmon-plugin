plugins {
    id("mcmonitor.java")
}

tasks.compileJava {
    options.release = 17
}

dependencies {
    compileOnly(libs.gson)
    testImplementation(libs.gson)
    testImplementation(project(":fake-site"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
