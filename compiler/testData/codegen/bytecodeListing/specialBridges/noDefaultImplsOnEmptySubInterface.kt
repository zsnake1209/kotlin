// FULL_JDK

interface MyList<T> : MutableList<T>
interface MySet<E> : MutableSet<E>
interface MyMap<K, V> : MutableMap<K, V>

interface MyMap2<X, Y> : MyMap<X, Y>
