plugins {
    `java-library`
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(providers.gradleProperty("rhworld.java").get().toInt())
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":engine"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = providers.gradleProperty("rhworld.engine.release").get().toInt()
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
