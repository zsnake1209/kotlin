import kotlinx.colorScheme.*

style(TextStyle.Custom.Custom0) {
    isFromSpek() && declaration.name in setOf("group", "context", "given")
}

style(TextStyle.Custom.Custom1) {
    isFromSpek() && declaration.name in setOf("action", "on")
}

style(TextStyle.Custom.Custom2) {
    isFromSpek() && declaration.name in setOf("it", "test")
}

fun Call.isFromSpek() =
        declaration.containingClass?.fqName?.startsWith("org.spekframework.spek2") ?: false