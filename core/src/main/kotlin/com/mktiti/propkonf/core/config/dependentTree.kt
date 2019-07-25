package com.mktiti.propkonf.core.config

import com.mktiti.propkonf.core.general.Either
import com.mktiti.propkonf.core.general.Left
import com.mktiti.propkonf.core.general.PropValue
import com.mktiti.propkonf.core.general.Right
import com.mktiti.propkonf.core.variable.MutableVarContextStack
import com.mktiti.propkonf.core.variable.VarContextStack
import com.mktiti.propkonf.core.variable.VarDependentToken
import java.util.*

sealed class DependentProperty(val name: String) {
    open operator fun get(parts: List<String>): PropValue<*>? = null

    internal abstract fun neededVars(): Set<String>

    abstract fun evaluate(evaluatedParentContext: VarContextStack): Property
}

internal class SimpleDependentProperty(
        name: String,
        internal val value: Either<PropValue<*>, VarDependentToken>
) : DependentProperty(name) {
    override fun toString() = "$name = ${value.unify(::propertyEscaped) { "Expression" } }"


    override fun neededVars(): Set<String> = when (value) {
        is Left -> emptySet()
        is Right -> value.value.neededVars().required
    }

    override fun evaluate(evaluatedParentContext: VarContextStack): SimpleProperty<*> {
        val propVal = when (value) {
            is Left -> value.value
            is Right -> value.value.eval(evaluatedParentContext).value
        }
        return SimpleProperty(name, propVal)
    }
}

class DependentBlock(
        name: String,
        val variables: List<DependentProperty>,
        val varContext: DependentVarContextStack
) : DependentProperty(name) {

    override operator fun get(parts: List<String>) = if (parts.isEmpty()) {
        null
    } else {
        variables.find { it.name == parts.first() }?.get(parts.drop(1))
    }

    override fun neededVars(): Set<String>
        = variables.flatMap(DependentProperty::neededVars).toSet() - varContext.allKeys() + varContext.neededVars()

    override fun evaluate(evaluatedParentContext: VarContextStack): Block {
        val evaluatedContext = varContext.evaluateLevel(evaluatedParentContext)
        return Block(
                name,
                variables.map { it.evaluate(evaluatedContext) }
        )
    }
}

open class DependentVarContextStack(
        private val parent: DependentVarContextStack? = null
) {
    internal val variables: MutableList<Pair<String, Either<PropValue<*>, VarDependentToken>>> = LinkedList()

    internal operator fun set(name: String, value: Either<PropValue<*>, VarDependentToken>) {
        variables += name to value
    }

    fun allKeys(): Set<String> = if (parent == null) {
        variables.map { it.first }.toSet()
    } else {
        variables.map { it.first }.toSet() + parent.allKeys()
    }

    fun neededVars(): Set<String> {
        val levelVals = mutableListOf<String>()
        val needed = mutableSetOf<String>()

        variables.forEach { (name, expr) ->
            val exprNeeds: Set<String> = when (expr) {
                is Left -> emptySet()
                is Right -> expr.value.neededVars().required
            }
            needed += exprNeeds - levelVals
            levelVals += name
        }

        return needed - (parent?.allKeys() ?: emptySet())
    }

    fun evaluateLevel(evaluatedParent: VarContextStack): VarContextStack =
        MutableVarContextStack(evaluatedParent).apply {
            variables.forEach { (key, value) ->
                when (value) {
                    is Left -> set(key, value.value)
                    is Right -> set(key, value.value.eval(this).value)
                }
            }
        }
}