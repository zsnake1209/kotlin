enum class ProgressionDirection {
    UNKNOWN {
        override fun asReversed() = UNKNOWN
    };

    abstract fun asReversed(): ProgressionDirection
}

fun box() = "OK"
