package com.mktiti.propkonf.core.general

import com.mktiti.propkonf.core.variable.InterpolatedStrDepToken
import com.mktiti.propkonf.core.variable.VarDependentToken
import com.mktiti.propkonf.core.variable.exprTokenize
import java.util.*

internal fun SourceStream.parseAnyString(): StringParseResult? {
    return when {
        jumpIfEq("|\"\"\"") -> StringParseResult(parseRawString().trimMargin("|"))
        jumpIfEq("\"\"\"") -> StringParseResult(parseRawString())
        jumpIfEq("\"") -> parseString()
        else -> null
    }
}

internal class StringParseResult(val parts: List<Either<out String, out VarDependentToken>>) {

    constructor(value: String) : this(listOf(Left(value)))

    fun getIfConstant(): String? {
        return when (parts.size) {
            0 -> ""
            1 -> (parts.first() as? Left)?.value
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
            '\n' -> failLex("Single string literal not closed (newline character before closing \")")
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

internal fun SourceStream.parseRawString(): String = StringBuilder().apply {
    while (!jumpIfEq("\"\"\"")) {
        append(next())
    }
}.toString()