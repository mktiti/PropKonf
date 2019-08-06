package com.mktiti.propkonf.core.general

import com.mktiti.propkonf.core.variable.InterpolatedStrDepToken
import com.mktiti.propkonf.core.variable.VarDependentToken
import com.mktiti.propkonf.core.variable.exprTokenize
import java.util.*

internal fun SourceStream.parseAnyString(): StringParseResult? = when {
    jumpIfEq("|\"\"\"") -> ConstStringResult(parseRawString().trimMargin("|"))
    jumpIfEq("\"\"\"") -> ConstStringResult(parseRawString())
    jumpIfEq("x\"") -> ConstStringResult(parseUninterpolated())
    jumpIfEq("\"") -> parseInterpolated()
    else -> null
}

internal sealed class StringParseResult {
    abstract val parts: List<Either<out String, out VarDependentToken>>

    abstract fun getIfConstant(): String?

    open fun <SR, VR> onResult(
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

internal class ConstStringResult(private val value: String) : StringParseResult() {
    override val parts: List<Either<out String, out VarDependentToken>> by lazy { listOf(Left<String, VarDependentToken>(value)) }

    override fun getIfConstant(): String = value

    override fun <SR, VR> onResult(onConstant: (String) -> SR, onVar: (InterpolatedStrDepToken) -> VR): Either<SR, VR>
            = Left(onConstant(value))
}

internal class InterpolatedStringResult(
        override val parts: List<Either<out String, out VarDependentToken>>
) : StringParseResult() {

    override fun getIfConstant(): String? {
        return when (parts.size) {
            0 -> ""
            1 -> (parts.first() as? Left)?.value
            else -> null
        }
    }
}

private fun SourceStream.parseStringInto(builder: StringBuilder, interpolator: SourceStream.() -> Unit) = with(builder) {
    loop@while (true) {
        when (val char = next()) {
            '\n' -> failLex("Single string literal not closed (newline character before closing \")")
            '"' -> break@loop
            '\\' -> {
                when (val special = next()) {
                    '\\' -> append('\\')
                    't' -> append('\t')
                    'b' -> append('\b')
                    'r' -> append('\r')
                    '\'' -> append('\'')
                    'n' -> append('\n')
                    '"' -> append('\"')
                    '$' -> append('\$')
                    else -> failLex("Illegal escaped character '$special', use '\\\\' to write '\\'")
                }
            }
            '$' -> interpolator()
            else -> append(char)
        }
    }
}

internal fun SourceStream.parseUninterpolated(): String = StringBuilder().apply {
    parseStringInto(this) {
        append('$')
    }
}.toString()

internal fun SourceStream.parseInterpolated(): StringParseResult {
    val builder = StringBuilder()
    val producers = LinkedList<Either<out String, out VarDependentToken>>()

    fun builderToRes() {
        val before = builder.toString()
        builder.clear()
        if (before.isNotEmpty()) {
            producers += Left(before)
        }
    }

    parseStringInto(builder) {
        if (next() != '{') {
            failLex("In interpolated string $ must be followed by {expression}, to write '$', use '\\$'")
        }

        builderToRes()

        val expr = exprTokenize() ?: failLex("Failed to parse expression")
        producers += Right(expr)
    }

    builderToRes()

    return when (producers.size) {
        0 -> ConstStringResult("")
        1 -> when (val only = producers.first) {
            is Left -> ConstStringResult(only.value)
            is Right -> InterpolatedStringResult(producers)
        }
        else -> InterpolatedStringResult(producers)
    }
}

internal fun SourceStream.parseRawString(): String = StringBuilder().apply {
    while (!jumpIfEq("\"\"\"")) {
        append(next())
    }
}.toString()