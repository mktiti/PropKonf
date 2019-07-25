package com.mktiti.propkonf.core.variable

import com.mktiti.propkonf.core.general.*
import java.util.*
import kotlin.math.max

internal fun ValueExpression.toInt(): Int = when (val prop = this.value) {
    is IntVal -> prop.value
    is TrueVal -> 1
    is FalseVal -> 0
    is StringVal -> {
        val value = prop.value
        value.toIntOrNull() ?: when {
            value == "true" -> 1
            value == "false" -> 0
            value.isBlank() -> 0
            else -> 1
        }
    }
}

internal fun ValueExpression.toBool(): Boolean = when (val prop = value) {
    is BoolVal -> prop.value
    is IntVal -> prop.value != 0
    is StringVal -> toInt() != 0
}

class ExpressionEvalException(message: String) : RuntimeException(message)

data class ExpressionDependencies(
        val required: Set<String> = emptySet(),
        val optional: Set<String> = emptySet()
)

internal sealed class ExpressionToken

internal sealed class VarDependentToken : ExpressionToken() {
    abstract fun neededVars(): ExpressionDependencies

    abstract fun eval(varContext: VarContextStack): ValueExpression
}

internal class SingleVarToken(
        private val name: String,
        private val optional: Boolean
) : VarDependentToken() {
    override fun neededVars()
        = if (optional) {
        ExpressionDependencies(optional = setOf(name))
        } else {
        ExpressionDependencies(required = setOf(name))
        }

    override fun eval(varContext: VarContextStack): ValueExpression = ValueExpression(
            varContext[name]
                    ?: if (optional) StringVal("") else throw ExpressionEvalException("Variable '$name' not set")
    )
}

internal class InterpolatedStrDepToken(private val parts: List<Either<out String, out VarDependentToken>>) : VarDependentToken() {
    override fun neededVars(): ExpressionDependencies
            = parts.filterIsInstance<Right<String, VarDependentToken>>()
            .fold(ExpressionDependencies()) { depAcc, expr ->
                val exprDep = expr.value.neededVars()
                ExpressionDependencies(
                        required = depAcc.required + exprDep.required,
                        optional = depAcc.optional + exprDep.optional
                )
            }

    override fun eval(varContext: VarContextStack): ValueExpression {
        val strValue = parts.map {
            when (it) {
                is Left -> it.value
                is Right -> it.value.eval(varContext).value.toString()
            }
        }.joinToString("")
        return strExpr(strValue)
    }
}

internal class GeneralDependantToken(private val expressions: List<ExpressionToken>) : VarDependentToken() {
    override fun neededVars(): ExpressionDependencies
        = expressions.filterIsInstance<VarDependentToken>()
            .fold(ExpressionDependencies()) { depAcc, expr ->
                val exprDep = expr.neededVars()
                ExpressionDependencies(
                        required = depAcc.required + exprDep.required,
                        optional = depAcc.optional + exprDep.optional
                )
            }

    override fun eval(varContext: VarContextStack): ValueExpression {
        val parts = expressions.map {
            when (it) {
                is VarDependentToken -> it.eval(varContext)
                is ConcreteToken -> it
            }
        }
        return collectionTraverser(parts).parseScope()
    }
}

internal sealed class ConcreteToken : ExpressionToken()

internal object OpenParen : ConcreteToken()
internal object CloseParen : ConcreteToken()

internal sealed class CalcExpr : ConcreteToken()

internal class ValueExpression(val value: PropValue<*>) : CalcExpr() {
    override fun toString() = value.toString()
}

internal sealed class OperatorExpr(val priority: Int) : CalcExpr() {
    abstract fun calc(leftVal: ValueExpression, rightVal: ValueExpression): ValueExpression
}

internal fun strExpr(value: String) = ValueExpression(StringVal(value))
internal fun intExpr(value: Int) = ValueExpression(IntVal(value))
internal fun boolExpr(value: Boolean) = ValueExpression(if (value) TrueVal else FalseVal)

internal sealed class IntOperatorExpr(priority: Int, val calcFun: (Int, Int) -> ValueExpression) : OperatorExpr(priority) {
    override fun calc(leftVal: ValueExpression, rightVal: ValueExpression)
            = calcFun(leftVal.toInt(), rightVal.toInt())
}

internal sealed class BoolOperatorExpr(priority: Int, val calcFun: (Boolean, Boolean) -> ValueExpression) : OperatorExpr(priority) {
    override fun calc(leftVal: ValueExpression, rightVal: ValueExpression)
            = calcFun(leftVal.toBool(), rightVal.toBool())
}

internal object Mult : IntOperatorExpr(6, { a, b -> intExpr(a * b) })
internal object Div : IntOperatorExpr(6, { a, b ->
    if (b == 0) {
        throw ExpressionEvalException("Tried to divide by zero")
    } else {
        intExpr(a / b)
    }
})
internal object Mod : IntOperatorExpr(6, { a, b -> intExpr(a % b) })

internal object Plus : IntOperatorExpr(5, { a, b -> intExpr(a + b) })
internal object Minus : IntOperatorExpr(5, { a, b -> intExpr(a - b) })

internal object Less : IntOperatorExpr(4, { a, b -> boolExpr(a < b) })
internal object More : IntOperatorExpr(4, { a, b -> boolExpr(a > b) })
internal object LessEq : IntOperatorExpr(4, { a, b -> boolExpr(a <= b) })
internal object MoreEq : IntOperatorExpr(4, { a, b -> boolExpr(a >= b) })

private fun eq(leftVal: ValueExpression, rightVal: ValueExpression): Boolean =
    if (leftVal.value is StringVal && rightVal.value is StringVal) {
        leftVal.value.value == rightVal.value.value
    } else {
        leftVal.toInt() == rightVal.toInt()
    }

internal object Eq : OperatorExpr(3) {
    override fun calc(leftVal: ValueExpression, rightVal: ValueExpression) =
            boolExpr(eq(leftVal, rightVal))
}
internal object NotEq : OperatorExpr(3) {
    override fun calc(leftVal: ValueExpression, rightVal: ValueExpression) =
            boolExpr(!eq(leftVal, rightVal))
}

internal object And : BoolOperatorExpr(2, { a, b -> boolExpr(a && b) })
internal object Or : BoolOperatorExpr(1, { a, b -> boolExpr(a || b) })

internal object OnTrue : OperatorExpr(0) {
    override fun calc(leftVal: ValueExpression, rightVal: ValueExpression): ValueExpression
        = if (leftVal.toBool()) rightVal else leftVal
}

internal object OnElse : OperatorExpr(0) {
    override fun calc(leftVal: ValueExpression, rightVal: ValueExpression): ValueExpression
            = if (leftVal.toBool()) leftVal else rightVal
}

internal class ExprNode(
        val operator: OperatorExpr,
        val next: ExpressionList
)

internal tailrec fun maxPriority(list: ExpressionList, max: Int = 0): Int
        = if (list.node == null) {
            max
        } else {
    maxPriority(list.node.next, max(list.node.operator.priority, max))
        }

internal class ExpressionList(
        val value: ValueExpression,
        val node: ExprNode?
) {
    val singleVal: ValueExpression?
        get() = if (node == null) value else null

    fun calcSingle(priority: Int): ExpressionList {
        return if (node == null) {
            return this
        } else {
            val operator = node.operator
            if (operator.priority == priority) {
                val newVal = operator.calc(value, node.next.value)
                ExpressionList(
                        newVal, node.next.node
                ).calcSingle(priority)
            } else {
                ExpressionList(
                        value,
                        ExprNode(node.operator, node.next.calcSingle(priority))
                )
            }
        }
    }

}

internal fun BackingTraverser<ConcreteToken>.parseScope(): ValueExpression {
    val levelValues = LinkedList<ValueExpression>()
    val levelOps = LinkedList<OperatorExpr>()

    loop@while (hasNext()) {
        when (val token = next()) {
            is OpenParen -> {
                levelValues += parseScope()
            }
            is CloseParen -> {
                break@loop
            }
            is ValueExpression -> {
                levelValues += token
            }
            is OperatorExpr -> {
                levelOps += token
            }
        }
    }

    val lastVal = levelValues.removeLast()

    var exprList = levelValues.zip(levelOps)
                    .foldRight(ExpressionList(lastVal, null)) { (value, op), next ->
                        ExpressionList(value, ExprNode(op, next))
                    }

    while (true) {
        val sv = exprList.singleVal
        if (sv != null) {
            return sv
        }

        val max = maxPriority(exprList)
        exprList = exprList.calcSingle(max)
    }
}