plugins {
    `java-library`
}

val generateVersionProperties by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources/version")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("META-INF/kaleido-wfe-sdk")
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

dependencies {
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.parameter.names)
    api(libs.slf4j.api)

    implementation(libs.jetty.server)
    implementation(libs.jetty.websocket.server)
    implementation(libs.jetty.websocket.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.slf4j.simple)
}
