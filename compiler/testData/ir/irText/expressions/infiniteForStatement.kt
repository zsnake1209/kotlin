fun testInfiniteFor() {
    for {}
}

fun testBreak() {
    var x = 0
    var y = 0
    for {
        if (++x > 3) break
        ++y
    }
}

fun testBreakWithLabel() {
    var x = 0
    var y = 0
    L1@for {
        for {
            if (++x > 3) break
            ++y
        }
    }
}