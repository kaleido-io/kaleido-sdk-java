plugins {
    `java-library`
}

dependencies {
    testImplementation(project(":workflow-engine-sdk"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.slf4j.simple)
}

// Default `test` task is disabled -- component tests require Docker.
// Use `./gradlew :workflow-engine-sdk-component-test:componentTest` explicitly.
tasks.named<Test>("test") {
    enabled = false
}

tasks.register<Test>("componentTest") {
    description = "Run component tests against a real WFE + Postgres (requires Docker)"
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    systemProperty("wfe.test.mode", System.getenv("WFE_TEST_MODE") ?: "")
    systemProperty("firefly.enterprise.dir", System.getenv("FIREFLY_ENTERPRISE_DIR") ?: "")
}

tasks.register<Exec>("startDb") {
    description = "Start Postgres for source-mode component tests"
    group = "verification"
    commandLine("docker", "compose", "-f", rootProject.file("component-test-source.yml").absolutePath, "up", "-d")
}

tasks.register<Exec>("startWfe") {
    description = "Start Postgres + WFE containers for Docker-mode component tests"
    group = "verification"
    commandLine("docker", "compose", "-f", rootProject.file("component-test-docker.yml").absolutePath, "up", "-d")
}

tasks.register<Exec>("teardown") {
    description = "Tear down all component test infrastructure"
    group = "verification"
    commandLine("sh", "-c",
        "docker compose -f ${rootProject.file("component-test-source.yml").absolutePath} down -v 2>/dev/null; " +
        "docker compose -f ${rootProject.file("component-test-docker.yml").absolutePath} down -v 2>/dev/null; true")
}

tasks.register<Exec>("componentTestClean") {
    description = "Remove all Docker containers and volumes created by component tests"
    group = "verification"
    commandLine("sh", "-c",
        "docker compose -f ${rootProject.file("component-test-source.yml").absolutePath} down -v 2>/dev/null; " +
        "docker compose -f ${rootProject.file("component-test-docker.yml").absolutePath} down -v 2>/dev/null; true")
}
