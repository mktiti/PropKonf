package hu.mktiti.propkonf.core.variable

import hu.mktiti.propkonf.core.general.PropValue

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