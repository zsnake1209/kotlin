// CONFLICT: Function 'localFunB' is already declared in function 'containNames'
fun containNames() {
    fun <caret>localFunA() = 11
    fun localFunB() = 12
}

// NAME: localFunB