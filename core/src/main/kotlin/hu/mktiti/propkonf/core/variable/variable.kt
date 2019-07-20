package hu.mktiti.propkonf.core.variable

import hu.mktiti.propkonf.core.general.PropValue

class VarContextStack(
        private val parent: VarContextStack? = null
) {

    private val variables: MutableMap<String, PropValue<*>> = HashMap()

    operator fun get(name: String): PropValue<*>?
            = variables[name] ?: parent?.get(name)

    operator fun set(name: String, value: PropValue<*>) {
        variables[name] = value
    }

}