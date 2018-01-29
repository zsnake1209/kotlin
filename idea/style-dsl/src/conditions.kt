package kotlinx.colorScheme

typealias Predicate<T> = (T) -> Boolean

class Rule(val condition: Predicate<Call>, val textStyle: TextStyle)

sealed class Condition : Predicate<Call> {
    class And(private val conditions: List<Condition>) : Condition() {
        override operator fun invoke(call: Call) = conditions.all { it(call) }
    }

    class Or(private val conditions: List<Condition>) : Condition() {
        override operator fun invoke(call: Call) = conditions.any { it(call) }
    }

    //    class Not(private val condition: Condition)
    class PackageCondition(private val packageName: String) : Condition() {
        override operator fun invoke(call: Call): Boolean {
            return call.declaration.packageName == packageName
        }
    }

    class ReceiverCondition(private val condition: (Receiver) -> Boolean) : Condition() {
        override operator fun invoke(call: Call): Boolean {
            return condition(call.receiver)
        }
    }
}
