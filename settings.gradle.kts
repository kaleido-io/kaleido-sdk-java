pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kaleido-sdk-java"

include("workflow-engine-sdk")
include("workflow-engine-sdk-spring-boot-starter")

includeBuild("samples")
