package com.github.h0tk3y.kotlinVersionChanger

import groovy.lang.GroovyRuntimeException
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase.CONVERSION
import java.io.File

enum class Repository { EAP, EAP12, DEV, LOCAL, CENTRAL }

interface VersionChangerArguments {
    val project: File
    val targetVersion: String
    val destination: File?
    val repository: Repository?
    val freeCompilerArgs: List<String>
}

fun transformProject(arguments: VersionChangerArguments) {
    val targetDir = copyOrInPlace(arguments.project, arguments.destination)
    val gradleScripts = getGradleScripts(targetDir).filter { it.name != "settings.gradle" }

    gradleScripts.forEach { scriptFile ->
        transformBuildscript(scriptFile, arguments)
    }
}

private fun transformBuildscript(scriptFile: File, arguments: VersionChangerArguments) {
    val fileLines = scriptFile.readLines()

    val visitor = DependencyVersionFinder(groupId = "org.jetbrains.kotlin")

    val astRoots: List<ASTNode> = try {
        if (fileLines.isNotEmpty())
            AstBuilder().buildFromString(CONVERSION, fileLines.joinToString("\n")) else
            emptyList()
    } catch (e: GroovyRuntimeException) {
        println("Ignoring malformed buildscript: $scriptFile")
        return
    }

    astRoots.filter { it !is ClassNode }.forEach { it.visit(visitor) }

    val lineReplacements = visitor.entryResolutions
            .filterIsInstance<ReplaceRangeWithVersion>()
            .groupBy { it.lineNumber }
            .map { (k, v) -> (k - 1) to v.sortedByDescending { it.range.start } }

    val resultLines = fileLines.withIndex().toMutableList()

    for ((line, replacements) in lineReplacements)
        for (r in replacements)
            resultLines[line] = with(resultLines[line]) {
                val replacement = "${r.insertPrefix}${arguments.targetVersion}${r.insertSuffix}"
                copy(value = if (r.range == value.length..value.length - 1)
                    value + replacement else
                    value.replaceRange(r.range, replacement))
            }

    val dslPluginLines = visitor.entryResolutions.filterIsInstance<RemovePluginDsl>()
    val pluginNames = dslPluginLines.map { it.pluginName }.distinct()

    val insertIntoRoot = buildString {
        if (dslPluginLines.any())
            append(pluginsDslRootReplacement(arguments, pluginNames) + "\n\n")
        if (visitor.entryResolutions.none { it is InsertRepositoryAtLine && !it.isBuildscript })
            arguments.repository?.let { append(repoBlock(it)) }
        if (arguments.freeCompilerArgs.isNotEmpty()) {
            append(buildFreeCompilerArgsOption(arguments.freeCompilerArgs))
        }
    }

    if (insertIntoRoot.isNotEmpty()) {
        val safePositionInRoot = visitor.entryResolutions.filterIsInstance<InsertRootBlocksAtLine>()
                                         .firstOrNull()?.lineNumber ?: 0
        resultLines.insertBeforeIndexedValue(safePositionInRoot,
                                             (if (safePositionInRoot > 0) "\n" else "") +
                                             insertIntoRoot +
                                             (if (safePositionInRoot > 0) "" else "\n"))
    }

    arguments.repository?.let { repo ->
        for (e in visitor.entryResolutions.filterIsInstance<InsertRepositoryAtLine>())
            resultLines.insertBeforeIndexedValueBy(e.lineNumber - 1) { s ->
                (s?.takeWhile { c -> c.isWhitespace() } ?: "") + "    " + repoString(repo)
            }
    }

    if (dslPluginLines.any()) {
        val indices = dslPluginLines.map { it.lineNumber }.distinct().sortedDescending()
        for (i in indices)
            resultLines.removeAt(i - 1)
    }

    val buildScriptBlock = buildString {
        if (visitor.entryResolutions.none { it is InsertRepositoryAtLine && it.isBuildscript })
            arguments.repository?.let { append(repoBlock(it) + "\n") }

        if (pluginNames.any())
            append(pluginsDslBuildscriptReplacement(arguments, pluginNames))
    }
    if (buildScriptBlock.isNotEmpty()) {
        resultLines.add(0, IndexedValue(0, "buildscript {\n" + buildScriptBlock.lines().filterNot { it.isNullOrEmpty() }.map { "    $it" }.joinToString("\n") + "\n}\n"))
    }

    scriptFile.writeText(resultLines.map { it.value }.joinToString("\n"))
}

private fun buildFreeCompilerArgsOption(arguments: List<String>): String {
    val preparedArguments = arguments.joinToString(separator = ",") { "\"$it\"" }
    return buildString {
        appendln("tasks.withType(org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile).all {")
        appendln("    kotlinOptions.freeCompilerArgs = kotlinOptions.freeCompilerArgs + [")
        appendln("        $preparedArguments")
        appendln("    ]")
        appendln("}")
    }
}

private fun pluginsDslBuildscriptReplacement(arguments: VersionChangerArguments, pluginNames: List<String>) = buildString {
    if (arguments.repository == null)
        append(repoBlock(Repository.CENTRAL) + "\n")
    append("dependencies {\n")
    for (p in pluginNames.map { classicArtifactNameByPortalName(it) }.distinct()) {
        append("    classpath 'org.jetbrains.kotlin:$p:${arguments.targetVersion}'\n")
    }
    append("}")
}

private fun pluginsDslRootReplacement(arguments: VersionChangerArguments, pluginNames: List<String>) =
        pluginNames.map { "apply plugin: '${classicPluginNameByPortalName(it)}'" }.joinToString("\n")

private fun classicArtifactNameByPortalName(portalName: String) = "kotlin-" + when (portalName) {
    "allopen", "spring" -> "allopen"
    "noarg", "jpa" -> "jpa"
    else -> "gradle-plugin"
}

private fun classicPluginNameByPortalName(portalName: String) = "kotlin" + when (portalName) {
    "jvm" -> ""
    else -> "-" + portalName.replace('.', '-')
}

private fun repoString(repository: Repository) = when (repository) {
    Repository.DEV -> "maven { url 'http://dl.bintray.com/kotlin/kotlin-dev' }"
    Repository.EAP -> "maven { url 'http://dl.bintray.com/kotlin/kotlin-eap-1.1' }"
    Repository.EAP12 -> "maven { url 'http://dl.bintray.com/kotlin/kotlin-eap-1.2' }"
    Repository.LOCAL -> "mavenLocal()"
    Repository.CENTRAL -> "mavenCentral()"
}

private fun repoBlock(repository: Repository) =
        "repositories {\n" +
        "    ${repoString(repository)}\n" +
        "}"