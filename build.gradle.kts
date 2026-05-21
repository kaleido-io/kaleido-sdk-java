plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.spotbugs) apply false
}

val sdkVersion = providers.gradleProperty("sdkVersion").getOrElse("26.5.0-rc.0")
val sdkGroup = "io.kaleido"

allprojects {
    group = sdkGroup
    version = sdkVersion
}

val publishedModules = setOf("workflow-engine-sdk", "workflow-engine-sdk-spring-boot-starter")

tasks.register<Exec>("componentTestClean") {
    description = "Remove all Docker containers and volumes created by component tests"
    group = "verification"
    commandLine("sh", "-c",
        "docker compose -f ${file("component-test-source.yml").absolutePath} down -v 2>/dev/null; " +
        "docker compose -f ${file("component-test-docker.yml").absolutePath} down -v 2>/dev/null; true")
}

subprojects {
    apply(plugin = "java-library")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        if (name in publishedModules) {
            withSourcesJar()
            withJavadocJar()
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Javadoc> {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:all,-missing", "-quiet")
            addBooleanOption("html5", true)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    if (name in publishedModules) {
        apply(plugin = "maven-publish")
        apply(plugin = "jacoco")

        tasks.named<JacocoReport>("jacocoTestReport") {
            dependsOn(tasks.named("test"))
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/kaleido-io/kaleido-sdk-java")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String? ?: ""
                        password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key") as String? ?: ""
                    }
                }
            }
            publications {
                register<MavenPublication>("mavenJava") {
                    from(components["java"])
                    pom {
                        url.set("https://github.com/kaleido-io/kaleido-sdk-java")
                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }
                        scm {
                            connection.set("scm:git:git://github.com/kaleido-io/kaleido-sdk-java.git")
                            url.set("https://github.com/kaleido-io/kaleido-sdk-java")
                        }
                    }
                }
            }
        }
    }
}
