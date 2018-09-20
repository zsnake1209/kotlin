fun test() {
    fun local() {}

    suspend fun localSuspend() {}

    val c = {}

    val cs: suspend () -> Unit = {}

    val lambda = {
        c()
    }

    val o = object : Runnable {
        override fun run() {}
    }

    val localCallable = ::local
    val testSuspendCallable = ::testSuspend
}

suspend fun testSuspend() {
    testSuspend()
    testSuspend()
}

// 2 INNERCLASS InnerClassAttributeKt\$test\$1 null local
// 2 INNERCLASS InnerClassAttributeKt\$test\$2 null localSuspend
// 2 INNERCLASS InnerClassAttributeKt\$test\$c\$1 null InnerClassAttributeKt\$test\$c\$1
// 2 INNERCLASS InnerClassAttributeKt\$test\$cs\$1 null InnerClassAttributeKt\$test\$cs\$1
// 2 INNERCLASS InnerClassAttributeKt\$test\$lambda\$1 null null
// 2 INNERCLASS InnerClassAttributeKt\$test\$o\$1 null null
// 2 INNERCLASS InnerClassAttributeKt\$testSuspend\$1 null testSuspend\$Continuation
// 2 INNERCLASS InnerClassAttributeKt\$test\$localCallable\$1 null InnerClassAttributeKt\$test\$localCallable\$1
// 2 INNERCLASS InnerClassAttributeKt\$test\$testSuspendCallable\$1 null InnerClassAttributeKt\$test\$testSuspendCallable\$1
