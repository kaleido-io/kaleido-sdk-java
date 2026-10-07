plugins {
    `java-library`
}

dependencies {
    api(project(":core-sdk"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.slf4j.simple)
}
