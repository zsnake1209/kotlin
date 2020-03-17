class NoModifiers

open <!REPEATED_MODIFIER!>open<!> class RepeatedModifier

open class OpenModifier {

    enum <!REPEATED_MODIFIER!>enum<!> class RepeatedModifierOnMember

    fun foo() {
        open <!REPEATED_MODIFIER!>open<!> class RepeatedModifierInFunctionBody
    }

}