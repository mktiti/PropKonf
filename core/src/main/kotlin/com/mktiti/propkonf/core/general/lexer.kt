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
    val positive = when {
        jumpIfEq("+") -> true
        jumpIfEq("-") -> false
        else -> true
    }

    val radix = when {
        jumpIfEq("0b") -> 2
        jumpIfEq("0o") -> 8
        jumpIfEq("0x") -> 16
        else -> 10
    }

    var hadDigit = false
    var sumValue = 0

    fun parseDigit(char: Char, radix: Int): Int? {
        val value = when (char.toUpperCase()) {
            in '0'..'9' -> char - '0'
            in 'A'..'F' -> char - 'A' + 10
            else -> return null
        }

        if (value >= radix) {
            failLex("Literal with radix $radix cannot contain character '$char'")
        }

        return value
    }

    fun checkNotOnlyZero() {
        if (hadDigit && sumValue == 0) {
            failLex("Integer literal can only start with 0, if it's a radix prefix - 0b (binary), 0o (octal), 0x (hexadecimal)")
        }
    }

    loop@while (hasNext()) {
        val char = next()

        when {
            char == '_' -> {
                checkNotOnlyZero()
                continue@loop
            }
            char.isWhitespace() -> break@loop
            else -> {
                when (val charVal = parseDigit(char, radix)) {
                    null -> {
                        back()
                        break@loop
                    }
                    else -> {
                        checkNotOnlyZero()
                        hadDigit = true
                        sumValue = sumValue * radix + charVal
                    }
                }
            }
        }
    }

    if (!hadDigit) {
        failLex("Integer literal expected")
    }

    return if (positive) sumValue else -sumValue
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