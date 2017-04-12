package com.github.h0tk3y.kotlinVersionChanger

internal fun IntRange.shift(x: Int) = IntRange(start + x, endInclusive + x)

internal fun IntRange.expandLeft(by: Int) = IntRange(start - by, endInclusive)
internal fun IntRange.shrinkLeft(by: Int) = expandLeft(-by)

internal fun IntRange.expandRight(by: Int) = IntRange(start, endInclusive + by)
internal fun IntRange.shrinkRight(by: Int) = expandRight(-by)

internal fun <T> MutableList<IndexedValue<T>>.insertBeforeIndexedValue(indexed: Int, t: T) =
        insertBeforeIndexedValueBy(indexed) { t }

internal fun <T> MutableList<IndexedValue<T>>.insertBeforeIndexedValueBy(indexed: Int, t: (T?) -> T) {
    val indexInList = withIndex().firstOrNull { it.index >= indexed }?.index ?: size
    val element = IndexedValue(indexed, t(this.getOrNull(indexInList)?.value))
    if (indexInList in indices)
        add(indexInList, element) else
        add(element)
}