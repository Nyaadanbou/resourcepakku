pluginManagement {
    repositories {
        mavenLocal() // 为了导入 "nyaadanbou-repositories"
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("nyaadanbou-repository") version "0.0.1-snapshot"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("local") {
            from(files("gradle/local.versions.toml"))
        }
    }
    versionCatalogs {
        create("libs") {
            from("cc.mewcraft.gradle:catalog:0.11-SNAPSHOT")
        }
    }
}

rootProject.name = "resourcepakku"
