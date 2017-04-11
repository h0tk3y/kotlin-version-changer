package com.github.h0tk3y.kotlinVersionChanger

import java.io.File

fun copyOrInPlace(source: File, destination: java.io.File?): java.io.File =
        destination?.let {
            if (destination.isDirectory)
                destination.deleteRecursively()
            source.copyRecursively(destination)
            destination
        } ?: source

fun getGradleScripts(dir: File) = dir.walk().asSequence().filter { it.isFile && it.extension == "gradle" }