fun box(): String {
    if ((42 ?: 239) != 42) return "Fail Int"

    return "OK"
}
