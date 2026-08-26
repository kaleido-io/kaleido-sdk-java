plugins {
    `java-library`
}

val generateVersionProperties by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources/version")
    inputs.property("sdkVersion", project.version.toString())
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("META-INF/kaleido-workflow-engine-sdk")
        dir.mkdirs()
        dir.resolve("version.properties").writeText("version=${project.version}\n")
    }
}

sourceSets.main {
    resources.srcDir(generateVersionProperties.map { it.outputs.files.singleFile })
}

tasks.named("processResources") {
    dependsOn(generateVersionProperties)
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

dependencies {
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.parameter.names)
    implementation(libs.zjsonpatch)
    implementation(libs.java.websocket)
    api(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)

    // Mints the certificates the TLS tests need; test-only, never exposed to consumers.
    testImplementation(libs.bouncycastle.bcpkix)
    testImplementation(libs.bouncycastle.bcprov)

    testRuntimeOnly(libs.slf4j.simple)

    "componentTestImplementation"(platform(libs.junit.bom))
    "componentTestImplementation"(libs.junit.jupiter)
    "componentTestImplementation"(libs.jackson.dataformat.yaml)
    "componentTestRuntimeOnly"(libs.slf4j.simple)
}

// Not part of `check` — needs a reachable workflow engine (see src/componentTest/README.md).
val componentTest by tasks.registering(Test::class) {
    description =
        "Runs component tests against a reachable workflow engine (see src/componentTest/README.md)."
    group = "verification"
    testClassesDirs = sourceSets["componentTest"].output.classesDirs
    classpath = sourceSets["componentTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}
