package structured.config

import hu.mktiti.kreator.property.structured.config.*
import hu.mktiti.kreator.property.structured.config.BoolLiteral
import hu.mktiti.kreator.property.structured.config.IntLiteral
import hu.mktiti.kreator.property.structured.config.NameDef
import hu.mktiti.kreator.property.structured.config.StringLiteral
import hu.mktiti.kreator.property.structured.config.VarLiteral
import structured.general.BackingTraverser
import structured.variable.VarContextStack
import java.util.LinkedList

sealed class PropValue(val name: String) {
    open operator fun get(parts: List<String>): PropValue? = null
}

sealed class SimpleVal<T>(name: String, val value: T) : PropValue(name) {
    override fun toString() = "$name = $value"
}

class StringVal(name: String, value: String) : SimpleVal<String>(name, value)
class IntVal(name: String, value: Int) : SimpleVal<Int>(name, value)
class BoolVal(name: String, value: Boolean) : SimpleVal<Boolean>(name, value)

class Block(name: String, val variables: List<PropValue>) : PropValue(name) {

    override operator fun get(parts: List<String>) = if (parts.isEmpty()) {
        null
    } else {
        variables.find { it.name == parts.first() }?.get(parts.drop(1))
    }

}

fun Block.prettyPrint(ident: Int = 0) {
    printIdent(ident)
    if (variables.isEmpty()) {
        println("$name {}")
    } else {
        println("$name {")
        prettyPrintVars(variables, ident + 4)
        printIdent(ident)
        println("}")
    }
}

internal fun printIdent(spaces: Int) {
    print(" ".repeat(spaces))
}

internal fun prettyPrintVars(vars: List<PropValue>, ident: Int = 0) {
    for (variable in vars) {
        when (variable) {
            is SimpleVal<*> -> {
                printIdent(ident)
                println(variable)
            }
            is Block -> variable.prettyPrint(ident)
        }
    }
}

internal fun parse(tokens: List<Token>) = BackingTraverser(tokens).parseBlock("", VarContextStack())

internal fun BackingTraverser<Token>.parseBlock(name: String, parentContext: VarContextStack): Block {
    val vars: MutableList<PropValue> = LinkedList()
    val varContext = parentContext.createChild()

    var wasCommented = false
    loop@while (hasNext()) {
        when (val token = next()) {
            is BlockComment -> {
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
                        is VarExpression -> exprValue.code(varContext)
                        is VarLiteral<*> -> exprValue
                        else -> error("Var definition for '${token.name}' not followed by expression assignment")
                    }
                } else {
                    error("Var definition for '${token.name}' not followed by expression assignment")
                }
            }
            else -> {
                error("Illegal token $token")
            }
        }
    }

    return Block(name, vars)
}

internal fun BackingTraverser<Token>.parseVar(name: String, varContextStack: VarContextStack): PropValue {
    fun next(): Token = safeNext() ?: error("Name def '$name' has not value assigned")

    return when (val token = next()) {
        is Assign -> {
            when (val varToken = next()) {
                is VarLiteral<*> -> varToken
                is VarExpression -> varToken.code(varContextStack)
                else -> error("Name def '$name' has bad value assigned")
            }.toVar(name)
        }
        is BlockStart -> {
            parseBlock(name, varContextStack)
        }
        else -> error("Invalid token $token")
    }

}

internal fun VarLiteral<*>.toVar(name: String): SimpleVal<*> = when (this) {
    is StringLiteral -> StringVal(name, value)
    is IntLiteral -> IntVal(name, value)
    is BoolLiteral -> BoolVal(name, value)
}