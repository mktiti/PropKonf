package com.mktiti.propkonf.core.config

import com.mktiti.propkonf.core.general.*
import com.mktiti.propkonf.core.variable.ExpressionEvalException
import com.mktiti.propkonf.core.variable.VarDependentToken
import java.util.*

class ParseException(message: String) : RuntimeException(message)

fun failParse(message: String): Nothing = throw ParseException(message)

internal fun parse(tokens: List<Token>, rootContext: DependentVarContextStack? = null): DependentBlock? = try {
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

internal fun BackingTraverser<Token>.parseBlock(name: String, parentContext: DependentVarContextStack?): DependentBlock {
    val vars: MutableList<DependentProperty> = LinkedList()
    val varContext = DependentVarContextStack(parentContext)

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
                    val varValue: Either<PropValue<*>, VarDependentToken> = when (val exprValue = next()) {
                        is VarLiteral<*> -> Left(exprValue.value)
                        is VarExpression -> Right(exprValue.producer)
                        else -> failParse("Var definition for '${token.name}' not followed by expression assignment")
                    }
                    varContext[token.name] = varValue
                } else {
                    failParse("Var definition for '${token.name}' not followed by expression assignment")
                }
            }
            else -> {
                failParse("Illegal token $token")
            }
        }
    }

    return DependentBlock(name, vars, varContext)
}

internal fun BackingTraverser<Token>.parseVar(name: String, varContextStack: DependentVarContextStack): DependentProperty {
    fun next(): Token = safeNext() ?: failParse("Name def '$name' has not value assigned")

    return when (val token = next()) {
        is Assign -> {
            val value: Either<PropValue<*>, VarDependentToken> = when (val varToken = next()) {
                is VarLiteral<*> -> Left(varToken.value)
                is VarExpression -> Right(varToken.producer)
                else -> failParse("Name def '$name' has bad value assigned")
            }
            SimpleDependentProperty(name, value)
        }
        is BlockStart -> {
            parseBlock(name, varContextStack)
        }
        else -> failParse("Invalid token $token")
    }

}