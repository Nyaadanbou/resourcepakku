plugins {
    id("resourcepakku-conventions.commons")
    id("cc.mewcraft.libraries-repository")
    id("cc.mewcraft.copy-jar-build")
    id("cc.mewcraft.copy-jar-docker")
}

project.ext.set("name", "resourcepakku")

group = "cc.mewcraft.resourcepakku"
version = "1.0.0"
description = "A resourcepack distributor running on Velocity platform."

repositories {
    nyaadanbouReleases()
    nyaadanbouPrivate()
}

dependencies {
    compileOnly(local.velocity); kapt(local.velocity)
    implementation(platform(libs.bom.caffeine))
    implementation(platform(libs.bom.creative))
    implementation(platform(libs.bom.configurate.extra.kotlin))

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
            relocate(it, "cc.mewcraft.resourcepakku.libs.$it")
        }
    }
}

buildCopy {
    fileName = "resourcepakku-${project.version}.jar"
    archiveTask = "shadowJar"
}

dockerCopy {
    containerId = "aether-minecraft-1"
    containerPath = "/minecraft/proxy/plugins/"
    fileMode = 0b110_100_100
    userId = 999
    groupId = 999
    archiveTask = "shadowJar"
}