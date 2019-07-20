package hu.mktiti.propkonf.core.config

import hu.mktiti.propkonf.core.general.PropValue

sealed class Property(val name: String) {
    open operator fun get(parts: List<String>): PropValue<*>? = null
}

class SimpleProperty<P : PropValue<*>>(name: String, internal val value: P) : Property(name) {
    override fun toString() = "$name = ${propertyEscaped(value)}"
}

class Block(name: String, val variables: List<Property>) : Property(name) {
    override operator fun get(parts: List<String>) = if (parts.isEmpty()) {
        null
    } else {
        variables.find { it.name == parts.first() }?.get(parts.drop(1))
    }
}