plugins {
    java
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.4"
}

group = "io.kaleido"
version = providers.gradleProperty("sampleVersion").getOrElse("0.1.0-SNAPSHOT")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

sourceSets {
    create("componentTest") {
        // Unlike `test`, custom source sets do not get main on the classpath by default.
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations["componentTestImplementation"].extendsFrom(configurations.implementation.get())
configurations["componentTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val sdkVersion = providers.gradleProperty("sdkVersion").getOrElse("26.5.0-rc.1")

dependencies {
    implementation("io.kaleido:workflow-engine-sdk:$sdkVersion")
    implementation("org.springframework.boot:spring-boot-starter")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")

    "componentTestImplementation"(platform("org.junit:junit-bom:5.11.4"))
    "componentTestImplementation"("org.junit.jupiter:junit-jupiter")
    "componentTestImplementation"("ch.qos.logback:logback-classic:1.5.38")
    "componentTestImplementation"("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // The SDK deserializes JSON into Java record parameters by name.
    options.compilerArgs.addAll(listOf("-parameters"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Component tests need a live workflow engine (the connector-toolkit compose
// stack) and are not part of `check` — run them explicitly with
// `./gradlew componentTest`, mirroring the SDK's own componentTest task.
val componentTest by tasks.registering(Test::class) {
    description = "Runs component tests against a live workflow engine."
    group = "verification"
    testClassesDirs = sourceSets["componentTest"].output.classesDirs
    classpath = sourceSets["componentTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

springBoot {
    mainClass.set("io.kaleido.workflowengine.sample.SampleProviderApp")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("provider")
        }
    }
}
