import net.ltgt.gradle.errorprone.errorprone


plugins {
    id("mcmonitor.java")
}

base {
    archivesName = "mcmonitor-client-fabric-${project.name}"
}

val shade = configurations.create("shade")

dependencies {
    "implementation"(project(":client-fabric:common"))
    shade(project(":client-fabric:common"))
}

tasks.named<Jar>("jar") {
    dependsOn(shade)
    from({ shade.map { zipTree(it) } })
    from(rootProject.file("LICENSE"))
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

// Mixin calls injector handlers by reflection and passes them every argument of the target; Error Prone cannot see that.
val mixinHandlers =
    listOf(
        "org.spongepowered.asm.mixin.injection.Inject",
        "org.spongepowered.asm.mixin.injection.ModifyVariable",
    ).joinToString(",")

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        option("UnusedMethod:ExemptingMethodAnnotations", mixinHandlers)
        option("Unused:methodAnnotationsExemptingParameters", mixinHandlers)
    }
}
