plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("com.gradleup.shadow") version "8.3.5"
}

application {
    mainClass.set("miwayomi.MainKt")

    applicationDefaultJvmArgs = listOf(
        "-Xmx512m",
        "-Xms64m",
        "-XX:MaxMetaspaceSize=256m",
        "-XX:+UseSerialGC",
        "-XX:+UseStringDeduplication",
        "-Dfile.encoding=UTF-8",
    )
}

dependencies {
    implementation(project(":source-api"))
    implementation(project(":core-common"))
    implementation(project(":android-compat"))

    implementation(libs.bundles.ktor)
    implementation(libs.slf4j.simple)

    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.asm)
    implementation(libs.asm.tree)

    implementation("de.femtopedia.dex2jar:dex-translator:2.4.38")
    implementation("de.femtopedia.dex2jar:dex-tools:2.4.38")

    implementation("net.dongliu:apk-parser:2.6.10")
}

tasks.shadowJar {
    archiveBaseName.set("miwayomi")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    manifest {
        attributes["Main-Class"] = "miwayomi.MainKt"
        attributes["Multi-Release"] = "true"
    }
}
