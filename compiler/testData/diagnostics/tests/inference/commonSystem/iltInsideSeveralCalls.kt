// FIR_IDENTICAL
// !DIAGNOSTICS: -UNUSED_PARAMETER -UNCHECKED_CAST

fun <T> consumeLongAndMaterialize(x: Long): T = null as T
fun consumeAny(x: Any) = x

fun main() {
    consumeAny(consumeLongAndMaterialize(3 * 1000))
}
