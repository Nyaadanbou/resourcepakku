plugins {
    id("neko.repositories") version "1.0"
    id("neko-kotlin")
}

project.ext.set("name", "NekoRp")

group = "cc.mewcraft.nekorp"
version = "1.0.0"
description = "Resource Pack Server for Minecraft"

dependencies {
    compileOnly(libs.proxy.velocity)
    annotationProcessor(libs.proxy.velocity)

    implementation("com.aliyun.oss:aliyun-sdk-oss:3.17.4")

    implementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
    implementation("javax.activation:activation:1.1-rev-1")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.4")
}
