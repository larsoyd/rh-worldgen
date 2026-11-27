plugins {
    `java-library`
    application
}

application {
    mainClass = "rhworld.engine.cli.Main"
    applicationName = "rhworld"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(providers.gradleProperty("rhworld.java").get().toInt())
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
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
    systemProperty("rhworld.samples", rootProject.file("samples").absolutePath)
    inputs.dir(rootProject.file("samples"))
    maxHeapSize = "1g"
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Runs the pinned benchmark and writes build/bench/report.json"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "rhworld.engine.cli.Main"
    val moduli = rootProject.file("samples/bench-moduli.json")
    val report = layout.buildDirectory.file("bench/report.json")
    inputs.file(moduli)
    outputs.file(report)
    outputs.upToDateWhen { false }
    args("bench", "--moduli", moduli.absolutePath, "--repeat", providers.gradleProperty("rhworld.bench.repeat").getOrElse("5"),
        "--report", report.get().asFile.absolutePath)
}
