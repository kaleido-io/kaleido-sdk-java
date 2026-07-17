pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/kaleido-io/kaleido-sdk-java")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull ?: ""
            }
        }
    }
}

rootProject.name = "workflow-engine-getting-started"

// Local dev: build against the sibling kaleido-sdk-java checkout instead of
// the published GitHub Packages artifact. Toggle with -PsdkIncludeBuild=true
// or by setting sdkIncludeBuild=true in gradle.properties.
val sdkIncludeBuild = providers.gradleProperty("sdkIncludeBuild").orNull?.toBoolean() ?: false
if (sdkIncludeBuild) {
    includeBuild("../..") {
        dependencySubstitution {
            substitute(module("io.kaleido:workflow-engine-sdk")).using(project(":workflow-engine-sdk"))
        }
    }
}
