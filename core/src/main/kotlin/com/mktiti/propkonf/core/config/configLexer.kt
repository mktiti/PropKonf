package com.mktiti.propkonf.core.config

import com.mktiti.propkonf.core.general.*
import com.mktiti.propkonf.core.variable.MutableVarContextStack
import com.mktiti.propkonf.core.variable.VarContextStack
import com.mktiti.propkonf.core.variable.VarDependentToken
import com.mktiti.propkonf.core.variable.exprTokenize
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

fun main() {
    while (true) {
        try {
            print(">")
            val command = readLine()?.split(" ") ?: return

            val path = command[0]
            val vars = command.drop(1).map {
                val split = it.split("=")
                split[0] to split.drop(1).joinToString("")
            }
            val rootContext = MutableVarContextStack().apply {
                vars.forEach { (key, value) -> set(key, StringVal(value)) }
            }

            if (path.isBlank()) break
            val tokens = tokenize(Paths.get(path))
            println(tokens)
            if (tokens != null) {
                val dependentBlock = parse(tokens)
                if (dependentBlock == null) {
                    System.err.println("Failed to parse dependent block")
                } else {
                    println("Evaluation requires: ${dependentBlock.neededVars()}")
                    dependentBlock.evaluate(rootContext).prettyPrint()
                }
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

internal object ValueComment : Token() {
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
        internal val producer: VarDependentToken
) : Token() {
    operator fun invoke(varContext: VarContextStack) = producer.eval(varContext)

    override fun toString() = "Expression"
}

internal class VarLiteral<T>(internal val value: PropValue<T>) : Token() {
    override fun toString() = "Val {$value}"
}

fun fullParse(path: Path, rootContext: VarContextStack? = null): Block? =
        loadFile(path).tokenize()?.let { parse(it) }?.evaluate(VarContextStack(rootContext))

fun fullParse(value: String, rootContext: VarContextStack? = null): Block? =
        tokenize(value)?.let { parse(it) }?.evaluate(VarContextStack(rootContext))

internal fun tokenize(path: Path): List<Token>? = loadFile(path).tokenize()

internal fun tokenize(value: String): List<Token>? = stringTraverser(value).tokenize()

internal fun SourceStream.tokenize(): List<Token>? =
        try {
            val list: MutableList<Token> = LinkedList()

            skipWhitespace()
            while (hasNext()) {
                val parsed: Token? = when (val next = next()) {
                    '#' -> ValueComment
                    '{' -> BlockStart
                    '}' -> BlockEnd
                    '=' -> Assign
                    '$' -> {
                        if (peek() == '{') {
                            next()
                            VarExpression(exprTokenize()
                                    ?: failLex("Invalid expression"))
                        } else {
                            VarDef(parseName())
                        }
                    }
                    '/' -> {
                        when (safeNext()) {
                            '/' -> {
                                while (hasNext() && next() != '\n') {}
                            }
                            '*' -> {
                                block@while (hasNext()) {
                                    while (hasNext() && next() != '*') {}
                                    when (safePeek()) {
                                        '/' -> {
                                            next()
                                            break@block
                                        }
                                        null -> throw LexingException("Block comment ('/*') not closed ('*/' missing)")
                                    }
                                }
                            }
                            else -> throw LexingException("Unexpected '/' character ('//', '/*' are valid commenting)")
                        }
                        null
                    }
                    else -> {
                        back()

                        if (next.isDigit() || next == '+' || next == '-') {
                            VarLiteral(IntVal(parseInt()))
                        } else {

                            parseAnyString()?.onResult(
                                    onConstant = { VarLiteral(StringVal(it)) },
                                    onVar = { VarExpression(it) }
                            )?.value() ?: when (val name = parseName()) {
                                "true" -> VarLiteral(TrueVal)
                                "false" -> VarLiteral(FalseVal)
                                else -> NameDef(name)
                            }
                        }
                    }
                }

                if (parsed != null) {
                    list += parsed
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