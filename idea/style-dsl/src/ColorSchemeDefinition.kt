package kotlinx.colorScheme

import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.templates.ScriptTemplateDefinition

class ColorSchemeDependenciesResolver : DependenciesResolver {
    override fun resolve(
            scriptContents: ScriptContents,
            environment: Environment
    ): DependenciesResolver.ResolveResult {
        return DependenciesResolver.ResolveResult.Success(
                ScriptDependencies(
                        imports = listOf("kotlinx.colorScheme.*")
                )
        )
    }
}


@ScriptTemplateDefinition(resolver = ColorSchemeDependenciesResolver::class, scriptFilePattern = ".*\\.color\\.kts")
open class ColorSchemeDefinition {
    val newStyles = ArrayList<TextStyle.New>()
    val rules = ArrayList<Rule>()

    fun newStyle(name: String, basedOn: TextStyle?): TextStyle {
        val newStyle = TextStyle.New(name, basedOn)
        newStyles.add(newStyle)
        return newStyle
    }

    fun style(style: TextStyle, condition: Call.() -> Boolean) {
        rules.add(Rule(condition, style))
    }
}

fun highlightCall(scheme: ColorSchemeDefinition, call: Call): TextStyle? {
    scheme.rules.forEach {
        if (it.condition(call)) return it.textStyle
    }
    return null
}