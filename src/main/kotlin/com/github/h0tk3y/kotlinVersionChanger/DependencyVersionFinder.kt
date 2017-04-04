package com.github.h0tk3y.kotlinVersionChanger

abstract class EntryResolution(val lineNumber: Int)

class ReplaceRangeWithVersion(lineNumber: Int,
                              val range: IntRange,
                              val insertPrefix: String = "",
                              val insertSuffix: String = "") : EntryResolution(lineNumber)

class RemovePluginDsl(lineNumber: Int,
                      val pluginName: String) : EntryResolution(lineNumber)

class InsertRootBlocksAtLine(lineNumber: Int): EntryResolution(lineNumber)

private val colonDependencySyntax = "([^:]*):([^:]*)(:(.*))?".toRegex()

private val pluginDslPattern = "this\\.id\\(org\\.jetbrains\\.kotlin\\.(plugin\\.)?(.*?)\\)\\.version\\(.*?\\)".toRegex()

class DependencyVersionFinder(val groupId: String) : org.codehaus.groovy.ast.CodeVisitorSupport() {
    val entryResolutions = mutableListOf<EntryResolution>()

    private var insideDependencies = false
    private var insidePluginsDsl = false

    private fun checkString(str: org.codehaus.groovy.ast.expr.ConstantExpression) {
        val match = colonDependencySyntax.matchEntire(str.text)!!
        val matchedVersion = match.groups.get(4)
        if (matchedVersion != null) {
            entryResolutions += ReplaceRangeWithVersion(
                    str.lineNumber,
                    matchedVersion.range.shift(str.columnNumber))
        } else if (match.groups.get(3) == null) {
            entryResolutions += ReplaceRangeWithVersion(
                    str.lineNumber,
                    (0..-1).shift(str.lastColumnNumber - 2),
                    insertPrefix = ":"
            )
        }
    }

    override fun visitMethodCallExpression(call: org.codehaus.groovy.ast.expr.MethodCallExpression) {
        val args = call.arguments
        if (insideDependencies) {
            if (args is org.codehaus.groovy.ast.expr.ArgumentListExpression) {
                for (arg in args.expressions
                        .filterIsInstance<org.codehaus.groovy.ast.expr.GStringExpression>()
                        .filter { it.text.startsWith(groupId) }) {
                    if (arg.strings.size == 1) {
                        val str = arg.strings.single()
                        checkString(str)
                    } else if (arg.strings.size == 2 && arg.values.size == 1) {
                        entryResolutions += ReplaceRangeWithVersion(
                                arg.lineNumber,
                                arg.values[0].run { columnNumber..lastColumnNumber }.shift(-2))
                    }
                }
                for (str in args.expressions
                        .filterIsInstance<org.codehaus.groovy.ast.expr.ConstantExpression>()
                        .filter { it.text.startsWith(groupId) }) {
                    checkString(str)
                }
            } else if (args is org.codehaus.groovy.ast.expr.TupleExpression) {
                val arg = args.expressions.singleOrNull()
                if (arg is org.codehaus.groovy.ast.expr.NamedArgumentListExpression && arg.mapEntryExpressions.any {
                    it.keyExpression.text == "group" &&
                    it.valueExpression.text == groupId
                }) {
                    val versionPair = arg.mapEntryExpressions.firstOrNull { it.keyExpression.text == "version" }
                    if (versionPair != null) {
                        val valueExpr = versionPair.valueExpression
                        if (valueExpr != null) {
                            entryResolutions += ReplaceRangeWithVersion(
                                    valueExpr.lineNumber,
                                    (valueExpr.columnNumber..valueExpr.lastColumnNumber).shrinkRight(2))
                        }
                    } else {
                        entryResolutions += ReplaceRangeWithVersion(
                                arg.lineNumber,
                                arg.lastColumnNumber.let { it..it - 1 },
                                insertPrefix = "version: \"", insertSuffix = "\"")
                    }
                }
            }
        }

        if (insidePluginsDsl) {
            pluginDslPattern.matchEntire(call.text)?.let { matchResult ->
                val pluginName = matchResult.groups[2]?.value
                if (pluginName != null) {
                    entryResolutions += RemovePluginDsl(call.lineNumber, pluginName)
                }
            }
        }

        if (call.methodAsString == "dependencies") {
            insideDependencies = true
            args.visit(this)
            insideDependencies = false
        }
        if (call.methodAsString == "plugins") {
            insidePluginsDsl = true
            args.visit(this)
            insidePluginsDsl = false
            entryResolutions += InsertRootBlocksAtLine(args.lastLineNumber)
        }

        super.visitMethodCallExpression(call)
    }
}