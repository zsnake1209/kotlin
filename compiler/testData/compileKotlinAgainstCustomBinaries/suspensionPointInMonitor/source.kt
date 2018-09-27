fun builder(c: suspend () -> Unit) {}

private val lock = Any()

suspend fun suspensionPoint() {}

fun test() {
    builder {
        synchronized(lock) {
            suspensionPoint()
        }

        inlineMe {
            suspensionPoint()
        }

        monitorInFinally(
            { suspensionPoint() },
            { suspensionPoint() }
        )
    }

    synchronized(lock) {
        builder {
            suspensionPoint()
        }
    }

    synchronized(lock) {
        object : SuspendRunnable {
            override suspend fun run() {
                suspensionPoint()
            }
        }
    }
}

interface SuspendRunnable {
    suspend fun run() {}
}