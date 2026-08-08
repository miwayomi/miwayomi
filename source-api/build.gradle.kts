plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core-common"))
    api(project(":android-compat"))

    api(libs.kotlinx.serialization.json)
    api(libs.injekt)
    api(libs.rxjava)
    api(libs.jsoup)
    api(libs.nanohttpd)
}
