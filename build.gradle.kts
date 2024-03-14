plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

project.ext.set("name", "NekoRp")

group = "cc.mewcraft.nekorp"
version = "1.0.0"
description = "A resourcepack distributor running on Velocity platform."

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven("https://repo.xenondevs.xyz/releases") {
        content {
            includeGroup("xyz.xenondevs.invui")
            includeGroup("xyz.xenondevs.configurate")
        }
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    // Configurate
    implementation("xyz.xenondevs.configurate:configurate-yaml:4.2.0-SNAPSHOT")
    implementation("xyz.xenondevs.configurate:configurate-extra-kotlin:4.2.0-SNAPSHOT")
    // Aliyun OSS
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.17.4")
    implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
    implementation("javax.activation:activation:1.1-rev-1")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.4")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    // Relocate the dependencies
    shadowJar {
        listOf(
            "com.aliyun.oss",
            "javax.xml.bind",
            "javax.activation",
            "org.glassfish.jaxb"
        ).forEach {
            relocate(it, "cc.mewcraft.nekorp.libs.$it")
        }
    }
}
