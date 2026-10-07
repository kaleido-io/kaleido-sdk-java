plugins {
    `java-library`
}

dependencies {
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.module.parameter.names)
    api(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.slf4j.simple)
}
