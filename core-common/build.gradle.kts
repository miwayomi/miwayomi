plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":android-compat"))

    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.serialization.json.okio)
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.okhttp.brotli)
    api(libs.okhttp.zstd)
    api(libs.okio)
    api(libs.rxjava)

    implementation(libs.sqlite.jdbc)

    implementation("org.graalvm.polyglot:polyglot:24.1.2")
    implementation("org.graalvm.js:js:24.1.2")
}
