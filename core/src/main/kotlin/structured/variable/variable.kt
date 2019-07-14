package structured.variable

import hu.mktiti.kreator.property.structured.config.VarLiteral

internal class VarContextStack(
        private val parent: VarContextStack? = null
) {

    private val variables: MutableMap<String, VarLiteral<*>> = HashMap()

    operator fun get(name: String): VarLiteral<*>?
            = variables[name] ?: parent?.get(name)

    operator fun set(name: String, value: VarLiteral<*>) {
        variables[name] = value
    }

    fun createChild() = VarContextStack(this)

}