package androidx.compose

actual class BitSet actual constructor() {
    private var words: IntArray = IntArray(1)

    fun ensureCapacity(index: Int) {
        if (index > words.size * SLOT_SIZE) {
            var n = words.size
            while (index > n * SLOT_SIZE) n *= 2
            words = words.copyOf(n)
        }
    }

    actual fun set(bitIndex: Int) {
        ensureCapacity(bitIndex)
        val slot = bitIndex / SLOT_SIZE
        words[slot] = words[slot].or(1.shl(bitIndex))
    }

    actual fun or(set: BitSet) {
        words = set.words
    }

    actual fun clear(bitIndex: Int) {
        ensureCapacity(bitIndex)
        val slot = bitIndex / SLOT_SIZE
        words[slot] = words[slot].and(1.shl(bitIndex).inv())
    }

    actual operator fun get(bitIndex: Int): Boolean {
        ensureCapacity(bitIndex)
        val slot = bitIndex / SLOT_SIZE
        return words[slot].and(1.shl(bitIndex)) != 0
    }

    companion object {
        const val SLOT_SIZE = 32
    }
}