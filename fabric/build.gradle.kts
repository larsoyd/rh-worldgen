plugins {
    id("net.fabricmc.fabric-loom") version "1.19.4"
}

base {
    archivesName = "rhworld"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(providers.gradleProperty("rhworld.java").get().toInt())
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:26.3")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.167.0+26.3")
    implementation(project(":engine"))
    include(project(":engine"))
    implementation(project(":mechanics"))
    include(project(":mechanics"))
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "rhworld-test"
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}

loom {
    runs {
        create("worldTest") {
            server()
            source(sourceSets.getByName("gametest"))
            runDir("build/worldtest")
            vmArg("-Drhworld.worldtest=" + (findProperty("rhworld.worldtest") ?: "none"))
            vmArg("-Drhworld.order=" + (findProperty("rhworld.order") ?: "0"))
            findProperty("rhworld.threads")?.let { vmArg("-Dmax.bg.threads=$it") }
            ideConfigGenerated(false)
        }
        matching { it.name in setOf("client", "server", "clientGameTest", "worldTest") }.configureEach {
            findProperty("rhworld.key")?.let { vmArg("-Drhworld.key=$it") }
        }
    }
}
