plugins {
    id("neko.repositories") version "1.0"
    kotlin("jvm") version "1.9.22"
    alias(libs.plugins.shadow)
}

project.ext.set("name", "nekorp")

group = "cc.mewcraft.nekorp"
version = "1.0.0"
description = "A resourcepack distributor running on Velocity platform."

dependencies {
    compileOnly(libs.proxy.velocity)
    annotationProcessor(libs.proxy.velocity)
    implementation(platform(libs.bom.caffeine))
    implementation(platform(libs.bom.configurate.yaml))
    implementation(platform(libs.bom.configurate.kotlin))

    // Aliyun OSS
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.17.4") {
        exclude("com.google.code.gson")
        exclude("org.slf4j")
    }
    // Required by Java 9+
    implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
    implementation("javax.activation:activation:1.1-rev-1")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.4")
}

kotlin {
    jvmToolchain(17)

    sourceSets {
        val main by getting {
            dependencies {
                compileOnly(kotlin("stdlib"))
            }
        }
        val test by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("stdlib"))
            }
        }
    }
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

    val inputJarPath = lazy { shadowJar.get().archiveFile.get().asFile.absolutePath }
    val finalJarName = lazy { "${ext.get("name")}-${project.version}.jar" }
    register<Copy>("copyJar") {
        group = "mewcraft"
        dependsOn(build)
        from(inputJarPath.value)
        into(layout.buildDirectory)
        rename("(?i)${project.name}.*\\.jar", finalJarName.value)
    }
}
