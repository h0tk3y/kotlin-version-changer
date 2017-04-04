package com.github.h0tk3y.kotlinVersionChanger

fun IntRange.shift(x: Int) = IntRange(start + x, endInclusive + x)

fun IntRange.expandLeft(by: Int) = IntRange(start - by, endInclusive)
fun IntRange.shrinkLeft(by: Int) = expandLeft(-by)

fun IntRange.expandRight(by: Int) = IntRange(start, endInclusive + by)
fun IntRange.shrinkRight(by: Int) = expandRight(-by)