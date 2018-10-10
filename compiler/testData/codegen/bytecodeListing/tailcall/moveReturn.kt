// WITH_RUNTIME

abstract class A {
    protected abstract fun optional(): String?
    protected abstract suspend fun slow(): String

    suspend fun optimized(): String {
        val value = optional()
        if(value != null){
            return value
        }
        else {
            return slow()
        }
    }

    suspend fun notOptimized(): String {
        return optional() ?: slow()
    }
}