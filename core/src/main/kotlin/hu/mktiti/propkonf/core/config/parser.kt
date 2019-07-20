package hu.mktiti.propkonf.core.config

import hu.mktiti.propkonf.core.general.BackingTraverser
import hu.mktiti.propkonf.core.general.PropValue
import hu.mktiti.propkonf.core.variable.ExpressionEvalException
import hu.mktiti.propkonf.core.variable.VarContextStack
import java.lang.RuntimeException
import java.util.*

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

class ParseException(message: String) : RuntimeException(message)

fun failParse(message: String): Nothing = throw ParseException(message)

internal fun parse(tokens: List<Token>, rootContext: VarContextStack? = null): Block? = try {
    BackingTraverser(tokens).parseBlock("", rootContext)
} catch (evalE: ExpressionEvalException) {
    System.err.println("Failed to parse block: failed to evaluate expression")
    System.err.println(evalE.message)
    null
} catch (pe: ParseException) {
    System.err.println("Failed to parse block")
    System.err.println(pe.message)
    null
}

internal fun BackingTraverser<Token>.parseBlock(name: String, parentContext: VarContextStack?): Block {
    val vars: MutableList<Property> = LinkedList()
    val varContext = VarContextStack(parentContext)

    var wasCommented = false
    loop@while (hasNext()) {
        when (val token = next()) {
            is ValueComment -> {
                wasCommented = true
            }
            is NameDef -> {
                val variable = parseVar(token.name, varContext)
                if (!wasCommented) {
                    vars += variable
                } else {
                    wasCommented = false
                }
            }
            is BlockEnd -> {
                break@loop
            }
            is VarDef -> {
                if (next() is Assign) {
                    varContext[token.name] = when (val exprValue = next()) {
                        is VarExpression -> exprValue(varContext)
                        is VarLiteral<*> -> exprValue.value
                        else -> failParse("Var definition for '${token.name}' not followed by expression assignment")
                    }
                } else {
                    failParse("Var definition for '${token.name}' not followed by expression assignment")
                }
            }
            else -> {
                failParse("Illegal token $token")
            }
        }
    }

    return Block(name, vars)
}

internal fun BackingTraverser<Token>.parseVar(name: String, varContextStack: VarContextStack): Property {
    fun next(): Token = safeNext() ?: failParse("Name def '$name' has not value assigned")

    return when (val token = next()) {
        is Assign -> {
            val value = when (val varToken = next()) {
                is VarLiteral<*> -> varToken.value
                is VarExpression -> varToken(varContextStack)
                else -> failParse("Name def '$name' has bad value assigned")
            }
            SimpleProperty(name, value)
        }
        is BlockStart -> {
            parseBlock(name, varContextStack)
        }
        else -> failParse("Invalid token $token")
    }

}