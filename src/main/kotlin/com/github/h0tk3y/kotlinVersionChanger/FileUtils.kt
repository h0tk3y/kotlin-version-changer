package com.github.h0tk3y.kotlinVersionChanger

fun copyOrInPlace(source: java.io.File, destination: java.io.File?): java.io.File =
        destination?.let {
            if (destination.isDirectory)
                destination.deleteRecursively()
            source.copyRecursively(destination)
            destination
        } ?: source

fun getGradleScripts(dir: java.io.File) = dir.walk().asSequence().filter { it.isFile && it.extension == "gradle" }