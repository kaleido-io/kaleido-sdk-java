plugins {
    `java-library`
}

dependencies {
    api(project(":workflow-engine-sdk"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.context)

    compileOnly(libs.spring.boot.actuator)
    compileOnly(libs.micrometer.core)
    compileOnly(libs.jakarta.annotation.api)

    annotationProcessor(platform(libs.spring.boot.bom))
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.spring.boot.starter.test)
}
