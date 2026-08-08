plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {

    api(files("libs/android.jar"))

    implementation("org.graalvm.polyglot:polyglot:24.1.2")
    implementation("org.graalvm.js:js:24.1.2")

    api("org.json:json:20240303")
}
