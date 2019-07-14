package hu.mktiti.kreator.property.structured.config

import hu.mktiti.kreator.property.structured.variable.*
import structured.config.Block
import structured.config.SimpleVal
import structured.config.parse
import structured.config.prettyPrint
import structured.general.*
import structured.variable.VarContextStack
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

fun main() {
    while (true) {
        try {
            print(">")
            val path = readLine()
            if (path == null || path.isBlank()) break
            val tokens = tokenize(Paths.get(path))
            println(tokens)
            if (tokens != null) {
                parse(tokens).prettyPrint()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

internal fun ValueExpression<*>.valLiteral() = when (this) {
    is IntExpression -> IntLiteral(value)
    is StringExpression -> StringLiteral(value)
    is BooleanExpression -> BoolLiteral(value)
}

// Token types
internal sealed class Token

internal class VarDef(internal val name: String) : Token() {
    override fun toString() = "Var {\$$name}"
}

internal class NameDef(internal val name: String) : Token() {
    override fun toString() = "Def {$name}"
}

internal object BlockComment : Token() {
    override fun toString() = "Block Comment"
}

internal object BlockStart : Token() {
    override fun toString() = "{"
}

internal object BlockEnd : Token() {
    override fun toString() = "}"
}

internal object Assign : Token() {
    override fun toString() = ":="
}

internal class VarExpression(
        internal val code: (VarContextStack) -> VarLiteral<*>
) : Token() {
    override fun toString() = "Expression"
}

internal sealed class VarLiteral<T>(internal val value: T) : Token() {
    override fun toString() = "Val {$value}"
}

internal class StringLiteral(value: String) : VarLiteral<String>(value)
internal class IntLiteral(value: Int) : VarLiteral<Int>(value)
internal class BoolLiteral(value: Boolean) : VarLiteral<Boolean>(value)

fun fullParse(path: Path): Block? = loadFile(path).tokenize()?.let(::parse)

fun fullParse(value: String): Block? = tokenize(value)?.let(::parse)

internal fun tokenize(path: Path): List<Token>? = loadFile(path).tokenize()

internal fun tokenize(value: String): List<Token>? = stringTraverser(value).tokenize()

internal fun SourceStream.tokenize(): List<Token>? =
        try {
            val list: MutableList<Token> = LinkedList()

            skipWhitespace()
            while (hasNext()) {
                list += when (val next = next()) {
                    '#' -> BlockComment
                    '{' -> BlockStart
                    '}' -> BlockEnd
                    '=' -> Assign
                    '"' -> when (val stringRes = parseAnyString()) {
                        is SimpleStringResult -> StringLiteral(stringRes.value)
                        is InterpolatedStringResult -> VarExpression { context ->
                            StringLiteral(stringRes.producer(context))
                        }
                    }
                    '$' -> {
                        if (peek() == '{') {
                            next()
                            val expression = exprTokenize() ?: failLex("Invalid expression")
                            VarExpression { context ->
                                expression.eval(context).valLiteral()
                            }
                        } else {
                            VarDef(parseName())
                        }
                    }
                    else -> {
                        back()
                        if (next.isDigit() || next == '+' || next == '-') {
                            IntLiteral(parseInt())
                        } else {
                            when (val name = parseName()) {
                                "true" -> BoolLiteral(true)
                                "false" -> BoolLiteral(false)
                                else -> NameDef(name)
                            }
                        }
                    }
                }

                skipWhitespace()
            }

            list
        } catch (ise: IllegalStateException) {
            System.err.println("Input has ended unexpectedly")
            null
        } catch (le: LexingException) {
            System.err.println("Input cannot be parsed: ${le.message}")
            null
        }