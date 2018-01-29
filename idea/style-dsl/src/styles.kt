package kotlinx.colorScheme

sealed class TextStyle {

    abstract class Preset : TextStyle() {
        object HtmlTag : Preset()
        object Keyword : Preset()
        object Label : Preset()
    }

    abstract class Custom : TextStyle() {
        object Custom0 : Custom()
        object Custom1 : Custom()
        object Custom2 : Custom()
        //..
        object Custom15 : Custom()
    }

    class New(name: String, basedOn: TextStyle?) : TextStyle()
}

