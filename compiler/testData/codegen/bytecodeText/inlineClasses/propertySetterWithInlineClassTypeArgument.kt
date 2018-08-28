// !LANGUAGE: +InlineClasses

inline class Str(val string: String)

class C {
    var s = Str("")
}

// 1 public final getS\(\)Ljava/lang/String;
// 0 public final setS-90215lrx\(Ljava/lang/String;\)V
// 1 public final setS-ZpO8Zga\(Ljava/lang/String;\)V