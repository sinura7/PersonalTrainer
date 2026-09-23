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
        // Kotlin's plugin markers come from Maven Central only. The Portal serves its own,
        // byte-different copies, which gradle/verification-metadata.xml does not pin; when
        // Central refused a request, Gradle fell through to them and reported a supply-chain
        // failure instead of the network error it was.
        gradlePluginPortal {
            content { excludeGroupByRegex("org\\.jetbrains\\.kotlin.*") }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PersonalTrainer"
include(":app")
