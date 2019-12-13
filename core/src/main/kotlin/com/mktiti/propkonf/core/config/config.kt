package com.mktiti.propkonf.core.config

import com.mktiti.propkonf.core.general.PropValue
import java.lang.IllegalArgumentException

sealed class Property(val name: String) {
    abstract operator fun get(parts: List<String>): PropValue<*>?
}

class SimpleProperty<P : PropValue<*>>(name: String, internal val value: P) : Property(name) {
    override fun toString() = "$name = ${propertyEscaped(value)}"

    override fun get(parts: List<String>) = if (parts.isEmpty()) {
        value
    } else {
        null
    }
}

class Block(name: String, val variables: List<Property>) : Property(name) {
    private fun child(parts: List<String>): Property? = variables.find { it.name == parts.first() }

    override operator fun get(parts: List<String>) = if (parts.isEmpty()) {
        null
    } else {
        child(parts)?.get(parts.drop(1))
    }

    fun view(parts: List<String>): Block? = if (parts.isEmpty()) {
        this
    } else {
        when (val c = child(parts)) {
            is SimpleProperty<*> -> throw IllegalArgumentException("View must be a composite block")
            is Block -> c.view(parts.drop(1))
            null -> null
        }
    }
}