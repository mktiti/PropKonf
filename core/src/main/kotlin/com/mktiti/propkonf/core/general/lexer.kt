package com.mktiti.propkonf.core.general

import com.mktiti.propkonf.core.variable.InterpolatedStrDepToken
import com.mktiti.propkonf.core.variable.VarDependentToken
import com.mktiti.propkonf.core.variable.exprTokenize
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
                    back()
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
    = if (jumpIfEq("\"\"")) {
        parseRawString()
    } else {
        parseString()
    }

internal class StringParseResult(val parts: List<Either<out String, out VarDependentToken>>) {

    constructor(value: String) : this(listOf(Left(value)))

    fun getIfConstant(): String? {
        return when (parts.size) {
            0 -> ""
            1 -> {
                val only = parts.first()
                if (only is Left) only.value else null
            }
            else -> null
        }
    }

    fun <SR, VR> onResult(
            onConstant: (String) -> SR,
            onVar: (InterpolatedStrDepToken) -> VR
    ): Either<SR, VR> {
        val constVal = getIfConstant()
        return if (constVal == null) {
            Right(onVar(InterpolatedStrDepToken(parts)))
        } else {
            Left(onConstant(constVal))
        }
    }

}

internal fun SourceStream.parseString(): StringParseResult {
    val builder = StringBuilder()
    val producers = LinkedList<Either<out String, out VarDependentToken>>()

    fun builderToRes() {
        val before = builder.toString()
        builder.clear()
        if (before.isNotEmpty()) {
            producers += Left(before)
        }
    }

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

                builderToRes()

                val expr = exprTokenize() ?: failLex("Failed to parse expression")
                producers += Right(expr)
            }
            else -> builder.append(char)
        }
    }
    builderToRes()
    if (producers.isEmpty()) {
        producers += Left("")
    }

    return StringParseResult(producers)
}

internal fun SourceStream.parseRawString(): StringParseResult {
    val builder = StringBuilder()

    while (!jumpIfEq("\"\"\"")) {
        builder.append(next())
    }

    return StringParseResult(builder.toString())
}