package com.mktiti.propkonf.core.variable

import com.mktiti.propkonf.core.general.*
import com.mktiti.propkonf.core.general.FalseVal
import com.mktiti.propkonf.core.general.TrueVal

open class VarContextStack(
        private val parent: VarContextStack? = null
) {
    protected val variables: MutableMap<String, PropValue<*>> = HashMap()

    operator fun get(name: String): PropValue<*>?
            = variables[name] ?: parent?.get(name)

}

class MutableVarContextStack(
        parent: VarContextStack? = null
) : VarContextStack(parent) {

    operator fun set(name: String, value: PropValue<*>) {
        variables[name] = value
    }

}

class VarContextBuilder(parent: VarContextStack? = null) {
    private val context = MutableVarContextStack(parent)

    fun int(key: String, value: Int) {
        context[key] = IntVal(value)
    }

    fun string(key: String, value: String) {
        context[key] = StringVal(value)
    }

    fun bool(key: String, value: Boolean) {
        context[key] = if (value) TrueVal else FalseVal
    }

    fun view(): VarContextStack = context

}

fun buildVarContext(parent: VarContextStack? = null, builder: VarContextBuilder.() -> Unit): VarContextStack = with(VarContextBuilder(parent)) {
    builder()
    view()
}
