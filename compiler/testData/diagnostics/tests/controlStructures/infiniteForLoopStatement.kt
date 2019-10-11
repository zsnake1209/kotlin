fun use(x: Int) = x

fun testInfinite() {
    var <!UNUSED_VARIABLE!>x<!> = 0
    for {
        use(<!TYPE_MISMATCH!>"abc"<!>)
    }
    <!UNREACHABLE_CODE!>use(x)<!>
}

fun testBreak() {
    var x = 0
    for {
        if (++x > 10) break
        use(x)
    }
    use(x)
}

fun testContinue() {
    var x = 0
    for {
        if (++x < 10) continue
        use(x)
    }
    <!UNREACHABLE_CODE!>use(x)<!>
}

fun testInfiniteForInExpressionPosition() =
    <!EXPRESSION_EXPECTED!>for {}<!>

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
    var <!UNUSED_VARIABLE!>x<!> = 0
    L1@for {
        L2@for {
            continue@L1
        }
    }
    <!UNREACHABLE_CODE!>use(x)<!>
}