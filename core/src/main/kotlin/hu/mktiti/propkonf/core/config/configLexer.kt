package hu.mktiti.propkonf.core.config

import hu.mktiti.propkonf.core.general.*
import hu.mktiti.propkonf.core.variable.VarContextStack
import hu.mktiti.propkonf.core.variable.exprTokenize
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
                parse(tokens)?.prettyPrint()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
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
        private val producer: (VarContextStack) -> PropValue<*>
) : Token() {
    operator fun invoke(varContext: VarContextStack) = producer(varContext)

    override fun toString() = "Expression"
}

internal class VarLiteral<T>(internal val value: PropValue<T>) : Token() {
    override fun toString() = "Val {$value}"
}

fun fullParse(path: Path, rootContext: VarContextStack? = null): Block? =
        loadFile(path).tokenize()?.let { parse(it, rootContext) }

fun fullParse(value: String, rootContext: VarContextStack? = null): Block? =
        tokenize(value)?.let { parse(it, rootContext) }

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
                        is SimpleStringResult -> VarLiteral(StringVal(stringRes.value))
                        is InterpolatedStringResult -> VarExpression { context ->
                            StringVal(stringRes.producer(context))
                        }
                    }
                    '$' -> {
                        if (peek() == '{') {
                            next()
                            val expression = exprTokenize() ?: failLex("Invalid expression")
                            VarExpression { context ->
                                expression.eval(context).value
                            }
                        } else {
                            VarDef(parseName())
                        }
                    }
                    else -> {
                        back()
                        if (next.isDigit() || next == '+' || next == '-') {
                            VarLiteral(IntVal(parseInt()))
                        } else {
                            when (val name = parseName()) {
                                "true" -> VarLiteral(TrueVal)
                                "false" -> VarLiteral(FalseVal)
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