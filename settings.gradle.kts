pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "rhworld"

include("engine")
for (module in listOf("mechanics", "verify", "fabric")) {
    if (file("$module/build.gradle.kts").exists()) include(module)
}
