// POPUP_CONFLICT: Name conflicts with other variable
fun containNames() {
    val <caret>localValA = 11
    val localValB = 12
}

// NAME: localValB