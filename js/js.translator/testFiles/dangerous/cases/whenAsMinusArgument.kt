package foo

fun box() : Boolean {
  var  i = 0
  var t = ++i + when(i) {
    3 -> 4
    1 -> 2
    0 -> 1
    else -> 100
  }
  if (t != 3) {
    return false
  }
  return true
}