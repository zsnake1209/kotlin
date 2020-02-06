// !LANGUAGE: +NewInference

// FILE: Convertor.java

public interface Convertor<Src, Dst> {
    Out<Dst> convert(Out<Src> o);
}

// FILE: main.kt

fun takeConvertor(c: Convertor<String, String>) {}

class Out<out T> {}

fun main(o: Out<Nothing?>) {
    takeConvertor(Convertor { o }) // NI: Null can not be a value of a non-null type TypeVariable(Dst)!
}
