fun use(x: Int) = x

fun testInfinite() {
    var x = 0
    for {
        ++x
    }
    use(x)
}

fun testBreak() {
    var x = 0
    for {
        if (++x > 10) break
        ++x
    }
    use(x)
}

fun testContinue() {
    var x = 0
    for {
        if (++x > 10) continue
        use(x)
    }
}

fun testBreakWithLabel() {
    var x = 0
    L1@for {
        L2@for {
            break@L1
        }
    }
    use(x)
}

fun testContinueWithLabel() {
    var x = 0
    L1@for {
        L2@for {
            continue@L1
        }
    }
    use(x)
}