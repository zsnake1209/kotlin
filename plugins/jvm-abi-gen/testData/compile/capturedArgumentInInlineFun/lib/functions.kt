package lib

import java.util.function.Function

inline fun anonymousIncrementer(x: Int): Function<Int, Int> {
    return object : Function<Int, Int> {
        override fun apply(y: Int) = x + y
    }
}

inline fun lambdaIncrementer(x: Int): (Int) -> Int =
    { y -> x + y }