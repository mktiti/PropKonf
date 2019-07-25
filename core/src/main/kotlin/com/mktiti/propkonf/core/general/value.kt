package com.mktiti.propkonf.core.general

sealed class PropValue<T>(internal val value: T) {
    override fun toString() = value.toString()
}

class StringVal(value: String) : PropValue<String>(value)
class IntVal(value: Int) : PropValue<Int>(value)

sealed class BoolVal(value: Boolean) : PropValue<Boolean>(value)
internal object TrueVal : BoolVal(true)
internal object FalseVal : BoolVal(false)

fun boolVal(value: Boolean): PropValue<Boolean> = if (value) TrueVal else FalseVal