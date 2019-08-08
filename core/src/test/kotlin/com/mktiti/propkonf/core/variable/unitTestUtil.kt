package com.mktiti.propkonf.core.variable

private inline fun <reified T> calculateAny(expression: String): T
        = (calculateExpression(expression)?.value?.value as? T) ?:
            throw AssertionError("Expression '$expression' did not produce proper typed value")


internal fun calculateBool(expression: String) = calculateAny<Boolean>(expression)

internal fun calculateInt(expression: String) = calculateAny<Int>(expression)

internal fun calculateString(expression: String) = calculateAny<String>(expression)