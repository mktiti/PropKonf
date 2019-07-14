package hu.mktiti.kreator.property.structured.variable

import structured.general.*
import structured.variable.VarContextStack
import java.util.*

fun main() {
    while (true) {
        try {
            print(">")
            val expression = readLine()
            if (expression == null || expression.isBlank()) break

            println(calculateExpression(expression))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

internal fun calculateExpression(expression: String): ValueExpression<*>? {
    return stringTraverser(expression).exprTokenize()?.eval(VarContextStack())
            ?: return null
}

internal fun SourceStream.exprTokenize(): GeneralDependantToken? =
        try {
            val list: MutableList<ExpressionToken> = LinkedList()

            skipWhitespace()
            loop@while (hasNext()) {
                list += when (val next = next()) {
                    '}' -> break@loop

                    '(' -> OpenParen
                    ')' -> CloseParen

                    '*' -> Mult
                    '/' -> Div
                    '%' -> Mod

                    '+', '-' -> when {
                        peek().isDigit() -> {
                            back()
                            IntExpression(parseInt())
                        }
                        next == '+' -> Plus
                        else -> Minus
                    }

                    '<' -> if (safePeek() == '=') {
                        next()
                        LessEq
                    } else {
                        Less
                    }
                    '>' -> if (safePeek() == '=') {
                        next()
                        MoreEq
                    } else {
                        More
                    }

                    '=', '!' -> if (safeNext() == '=') {
                        if (next == '=') Eq else NotEq
                    } else {
                        failLex("Failed to parse operator")
                    }

                    '&' -> if (safeNext() == '&') {
                        And
                    } else {
                        failLex("Failed to parse operator")
                    }
                    '|' -> if (safeNext() == '|') {
                        Or
                    } else {
                        failLex("Failed to parse operator")
                    }

                    '?' -> OnTrue
                    ':' -> OnElse

                    '"' -> when (val stringRes = parseAnyString()) {
                        is SimpleStringResult -> StringExpression(stringRes.value)
                        is InterpolatedStringResult -> InterpolatedStringToken(stringRes::invoke)
                    }

                    else -> {
                        back()
                        if (next.isDigit()) {
                            IntExpression(parseInt())
                        } else {
                            when (val name = parseName()) {
                                "true" -> BooleanExpression(true)
                                "false" -> BooleanExpression(false)
                                else -> SingleVarToken(name)
                            }
                        }
                    }
                }

                skipWhitespace()
            }

            GeneralDependantToken(list)
        } catch (ise: IllegalStateException) {
            System.err.println("Input has ended unexpectedly")
            null
        } catch (le: LexingException) {
            System.err.println("Input cannot be parsed: ${le.message}")
            null
        }