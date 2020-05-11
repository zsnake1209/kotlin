
public interface MutableListIterator</*0*/ T> : kotlin.collections.ListIterator<T>, kotlin.collections.MutableIterator<T> {
    public abstract fun add(/*0*/ element: T): kotlin.Unit
    public abstract override /*2*/ fun hasNext(): kotlin.Boolean
    public abstract override /*2*/ fun next(): T
    public abstract override /*1*/ fun remove(): kotlin.Unit
    public abstract fun set(/*0*/ element: T): kotlin.Unit
}

public interface MutableMap</*0*/ K, /*1*/ V> : kotlin.collections.Map<K, V> {
    public abstract override /*1*/ val entries: kotlin.collections.MutableSet<kotlin.collections.MutableMap.MutableEntry<K, V>>
        public abstract override /*1*/ fun <get-entries>(): kotlin.collections.MutableSet<kotlin.collections.MutableMap.MutableEntry<K, V>>
    public abstract override /*1*/ val keys: kotlin.collections.MutableSet<K>
        public abstract override /*1*/ fun <get-keys>(): kotlin.collections.MutableSet<K>
    public abstract override /*1*/ val values: kotlin.collections.MutableCollection<V>
        public abstract override /*1*/ fun <get-values>(): kotlin.collections.MutableCollection<V>
    public abstract fun clear(): kotlin.Unit
    public abstract fun put(/*0*/ key: K, /*1*/ value: V): V?
    public abstract fun putAll(/*0*/ from: kotlin.collections.Map<out K, V>): kotlin.Unit
    public abstract fun remove(/*0*/ key: K): V?

    public interface MutableEntry</*0*/ K, /*1*/ V> : kotlin.collections.Map.Entry<K, V> {
        public abstract fun setValue(/*0*/ newValue: V): V
    }
}

public interface MutableSet</*0*/ E> : kotlin.collections.Set<E>, kotlin.collections.MutableCollection<E> {
    public abstract override /*1*/ fun add(/*0*/ element: E): kotlin.Boolean
    public abstract override /*1*/ fun addAll(/*0*/ elements: kotlin.collections.Collection<E>): kotlin.Boolean
    public abstract override /*1*/ fun clear(): kotlin.Unit
    public abstract override /*2*/ fun iterator(): kotlin.collections.MutableIterator<E>
    public abstract override /*1*/ fun remove(/*0*/ element: E): kotlin.Boolean
    public abstract override /*1*/ fun removeAll(/*0*/ elements: kotlin.collections.Collection<E>): kotlin.Boolean
    public abstract override /*1*/ fun retainAll(/*0*/ elements: kotlin.collections.Collection<E>): kotlin.Boolean
}

public interface RandomAccess {
}

public interface Set</*0*/ out E> : kotlin.collections.Collection<E> {
    public abstract override /*1*/ val size: kotlin.Int
        public abstract override /*1*/ fun <get-size>(): kotlin.Int
    public abstract override /*1*/ fun contains(/*0*/ element: E): kotlin.Boolean
    public abstract override /*1*/ fun containsAll(/*0*/ elements: kotlin.collections.Collection<E>): kotlin.Boolean
    public abstract override /*1*/ fun isEmpty(): kotlin.Boolean
    public abstract override /*1*/ fun iterator(): kotlin.collections.Iterator<E>
}

public abstract class ShortIterator : kotlin.collections.Iterator<kotlin.Short> {
    /*primary*/ public constructor ShortIterator()
    public final override /*1*/ fun next(): kotlin.Short
    public abstract fun nextShort(): kotlin.Short
}

@kotlin.SinceKotlin(version = "1.3") @kotlin.ExperimentalUnsignedTypes public abstract class UByteIterator : kotlin.collections.Iterator<kotlin.UByte> {
    /*primary*/ public constructor UByteIterator()
    public final override /*1*/ fun next(): kotlin.UByte
    public abstract fun nextUByte(): kotlin.UByte
}

@kotlin.SinceKotlin(version = "1.3") @kotlin.ExperimentalUnsignedTypes public abstract class UIntIterator : kotlin.collections.Iterator<kotlin.UInt> {
    /*primary*/ public constructor UIntIterator()
    public final override /*1*/ fun next(): kotlin.UInt
    public abstract fun nextUInt(): kotlin.UInt
}

@kotlin.SinceKotlin(version = "1.3") @kotlin.ExperimentalUnsignedTypes public abstract class ULongIterator : kotlin.collections.Iterator<kotlin.ULong> {
    /*primary*/ public constructor ULongIterator()
    public final override /*1*/ fun next(): kotlin.ULong
    public abstract fun nextULong(): kotlin.ULong
}

@kotlin.SinceKotlin(version = "1.3") @kotlin.ExperimentalUnsignedTypes public abstract class UShortIterator : kotlin.collections.Iterator<kotlin.UShort> {
    /*primary*/ public constructor UShortIterator()
    public final override /*1*/ fun next(): kotlin.UShort
    public abstract fun nextUShort(): kotlin.UShort
}
