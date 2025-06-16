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
    plugins {
        id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Boat Controller"
include(":app")
include(":gvr-libraries:base")
include(":gvr-libraries:common")
include(":gvr-libraries:audio")

project(":gvr-libraries:base").projectDir = file("libs/gvr/libraries/base")
project(":gvr-libraries:common").projectDir = file("libs/gvr/libraries/common")
project(":gvr-libraries:audio").projectDir = file("libs/gvr/libraries/audio")