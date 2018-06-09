/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin

/**
 * Represents a readable sequence of [Char] values.
 */
public interface CharSequence {
    /**
     * Returns the length of this character sequence.
     */
    public val length: Int

    /**
     * Returns the character at the specified [index] in this character sequence.
     */
    public operator fun get(index: Int): Char

    /**
     * Returns a new character sequence that is a subsequence of this character sequence,
     * starting at the specified [startIndex] and ending right before the specified [endIndex].
     *
     * @param startIndex the start index (inclusive).
     * @param endIndex the end index (exclusive).
     */
    public fun subSequence(startIndex: Int, endIndex: Int): CharSequence

    /**
     * Returns a string consisting of characters exactly the same and in the same order
     * as in this character sequence.
     */
    public override fun toString(): String
}
