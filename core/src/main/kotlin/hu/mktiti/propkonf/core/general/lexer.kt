package hu.mktiti.propkonf.core.general

import hu.mktiti.propkonf.core.variable.VarContextStack
import hu.mktiti.propkonf.core.variable.exprTokenize
import java.util.*

class LexingException(message: String) : RuntimeException(message)

internal fun failLex(message: String): Nothing {
    throw LexingException(message)
}

internal fun SourceStream.skipWhitespace() {
    while (safePeek()?.isWhitespace() == true) {
        next()
    }
}

internal fun SourceStream.parseName(): String {
    var first = true
    val builder = StringBuilder()

    fun inName(char: Char): Boolean {
        return char.toLowerCase() in ('a' .. 'z') ||
            (!first && (char == '-' || char == '_'  || char.isDigit()))
    }

    loop@while (hasNext()) {
        val char = next()
        if (inName(char)) {
            builder.append(char)
        } else {
            back()
            break@loop
        }

        first = false
    }

    if (first) {
        failLex("Name def is empty")
    }

    return builder.toString()
}

internal fun SourceStream.parseInt(): Int {
    var first = true
    var positive = true
    var hadDigit = false

    var value = 0

    loop@while (hasNext()) {
        val digit = next()
        when {
            digit == '+' || digit == '-' -> {
                if (first) {
                    positive = digit == '+'
                } else {
                    break@loop
                }
            }
            digit.isDigit() -> {
                hadDigit = true
                value = value * 10 + (digit - '0')
            }
            digit.isWhitespace() -> break@loop
            digit != '_' -> {
                back()
                break@loop
            }
        }
        first = false
    }

    if (!hadDigit) {
        failLex("Integer literal expected")
    }

    return if (positive) value else -value
}

internal fun SourceStream.parseAnyString(): StringParseResult
    = if (nTimes('"', 2)) {
        parseRawString()
    } else {
        parseString()
    }

internal sealed class StringParseResult {
    abstract operator fun invoke(varContextStack: VarContextStack): String
}

internal class SimpleStringResult(internal val value: String) : StringParseResult() {
    override operator fun invoke(varContextStack: VarContextStack) = value
}

internal class InterpolatedStringResult(val producer: (VarContextStack) -> String) : StringParseResult() {
    override operator fun invoke(varContextStack: VarContextStack) = producer(varContextStack)
}

internal fun SourceStream.parseString(): StringParseResult {
    val builder = StringBuilder()

    val producers = LinkedList<StringParseResult>()

    loop@while (true) {
        when (val char = next()) {
            '"' -> break@loop
            '\\' -> {
                when (val special = next()) {
                    '\\' -> builder.append('\\')
                    't' -> builder.append('\t')
                    'b' -> builder.append('\b')
                    'r' -> builder.append('\r')
                    '\'' -> builder.append('\'')
                    'n' -> builder.append('\n')
                    '"' -> builder.append('\"')
                    '$' -> builder.append('\$')
                    else -> failLex("Illegal escaped character '$special', use '\\\\' to write '\\'")
                }
            }
            '$' -> {
                if (next() != '{') {
                    failLex("In interpolated string $ must be followed by {expression}, to write '$', use '\\$'")
                }

                producers += SimpleStringResult(builder.toString())
                builder.clear()

                val expr = exprTokenize() ?: failLex("Failed to parse expression")
                producers += InterpolatedStringResult { context ->
                    expr.eval(context).value.toString()
                }
            }
            else -> builder.append(char)
        }
    }
    producers += SimpleStringResult(builder.toString())

    return if (producers.size == 1) {
        producers.first
    } else {
        InterpolatedStringResult { context ->
            producers.joinToString(separator = "") { prod ->
                prod.invoke(context)
            }
        }
    }
}

internal fun <T> BackingTraverser<T>.nTimes(field: T, count: Int = 3): Boolean =
    if (count == 0) {
        true
    } else if (!hasNext()) {
        false
    } else if (next() == field) {
        if (nTimes(field, count - 1)) {
            true
        } else {
            back()
            false
        }
    } else {
        back()
        false
    }

internal fun SourceStream.parseRawString(): SimpleStringResult {
    val builder = StringBuilder()

    while (!nTimes('"')) {
        builder.append(next())
    }

    return SimpleStringResult(builder.toString())
}