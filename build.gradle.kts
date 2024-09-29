plugins {
    id("nekorp-conventions.commons")
    id("nyaadanbou-conventions.repositories")
    id("nyaadanbou-conventions.copy-jar")
}

project.ext.set("name", "nekorp")

group = "cc.mewcraft.nekorp"
version = "1.0.0"
description = "A resourcepack distributor running on Velocity platform."

dependencies {
    compileOnly(local.velocity)
    kapt(local.velocity)
    implementation(platform(libs.bom.caffeine))
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

    copyJar {
        environment = "velocity"
        jarFileName = "nekorp-${project.version}.jar"
    }
}
