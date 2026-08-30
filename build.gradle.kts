plugins {
    application
}

group = "io.kaos"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "io.kaos.app.KaosApplication"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.1")

    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

val localStatus by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the KAOS status smoke check with deterministic local configuration."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args("status")
    systemProperty("kaos.app.name", "KAOS")
}

val localHelp by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the KAOS help smoke check with deterministic local configuration."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args("help")
    systemProperty("kaos.app.name", "KAOS")
    mustRunAfter(localStatus)
}

localStatus {
    mustRunAfter(tasks.named("build"))
}

tasks.register("verifyLocal") {
    group = "verification"
    description = "Builds, tests, packages, and smoke-runs the local KAOS application."
    dependsOn(tasks.named("build"), localStatus, localHelp)

    doLast {
        logger.lifecycle(
            "KAOS local verification passed: build, tests, package, status, and help."
        )
    }
}
