package com.github.h0tk3y.kotlinVersionChanger

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.runMain

class CliArguments(parser: ArgParser) : VersionChangerArguments {
    override val project by parser.storing(
            "--project",
            help = "project root directory",
            transform = { java.io.File(this) })
    override val targetVersion: String by parser.storing(
            "--version",
            help = "target Kotlin version")
    override val destination by parser.storing<java.io.File?>(
            "--destination",
            help = "destination to copy the project, process in place if not provided",
            transform = { java.io.File(this) })
            .default(null)
    override val repository by parser.storing<Repository?>(
            "--repository",
            help = "repository to add to buildscript and project, one of DEV, EAP, LOCAL",
            transform = { Repository.valueOf(toUpperCase()) })
            .default(null)
}

fun main(args: Array<String>) = CliArguments(com.xenomachina.argparser.ArgParser(args))
        .runMain("kotlin-version-changer", f = ::transformProject)

