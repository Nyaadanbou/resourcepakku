@file:Suppress("UnstableApiUsage")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        maven("https://repo.mewcraft.cc/releases")
        maven("https://repo.mewcraft.cc/private") {
            credentials {
                username = providers.gradleProperty("nyaadanbou.mavenUsername").getOrElse("")
                password = providers.gradleProperty("nyaadanbou.mavenPassword").getOrElse("")
            }
        }
    }
    versionCatalogs {
        create("local") {
            from(files("gradle/local.versions.toml"))
        }
    }
    versionCatalogs {
        create("libs") {
            from("cc.mewcraft.gradle:catalog:1.0-SNAPSHOT")
        }
    }
}

rootProject.name = "resourcepakku"
