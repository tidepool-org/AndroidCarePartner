pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "Loop Shadow"
include(":app")
includeBuild("TidepoolKotlinAPI") {
    dependencySubstitution {
        substitute(module("org.tidepool.api:TidepoolKotlinAPI")).using(project(":lib"))
    }
}
