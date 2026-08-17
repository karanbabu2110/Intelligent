plugins {
    application
}

group = "io.kaos"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "io.kaos.app.KaosApplication"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
