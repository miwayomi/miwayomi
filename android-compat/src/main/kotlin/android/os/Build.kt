package android.os

object Build {

    const val MANUFACTURER: String = "miwayomi"
    const val MODEL: String = "miwayomi-server"
    const val BRAND: String = "miwayomi"
    const val DEVICE: String = "miwayomi"
    const val PRODUCT: String = "miwayomi"
    const val HARDWARE: String = "miwayomi"
    val SUPPORTED_ABIS: Array<String> = arrayOf("x86_64", "amd64")
    const val FINGERPRINT: String = "miwayomi/generic"

    object VERSION {
        const val RELEASE: String = "13"
        const val SDK_INT: Int = 33
        const val CODENAME: String = "REL"
        const val INCREMENTAL: String = "1"
    }

    object VERSION_CODES {
        const val BASE = 1
        const val O = 26
        const val O_MR1 = 27
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val S_V2 = 32
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
    }
}
