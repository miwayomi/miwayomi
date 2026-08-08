package tachiyomi.core.common.preference

enum class TriState {
    DISABLED,
    ENABLED_IS,
    ENABLED_NOT,
    ;

    fun next(): TriState {
        return when (this) {
            DISABLED -> ENABLED_IS
            ENABLED_IS -> ENABLED_NOT
            ENABLED_NOT -> DISABLED
        }
    }
}
