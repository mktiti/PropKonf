package hu.mktiti.kreator.property.structured.variable

import hu.mktiti.kreator.property.structured.config.BoolLiteral
import hu.mktiti.kreator.property.structured.config.IntLiteral
import hu.mktiti.kreator.property.structured.config.StringLiteral
import hu.mktiti.kreator.property.structured.config.VarLiteral
import structured.general.BackingTraverser
import structured.general.collectionTraverser
import structured.general.failLex
import structured.variable.VarContextStack
import java.util.*
import kotlin.math.max

internal fun VarLiteral<*>.varExpression() = when (this) {
    is IntLiteral -> IntExpression(value)
    is StringLiteral -> StringExpression(value)
    is BoolLiteral -> BooleanExpression(value)
}

internal fun ValueExpression<*>.toInt() = when (this) {
    is IntExpression -> value
    is BooleanExpression -> if (value) 1 else 0
    is StringExpression -> {
        val intVal = value.toIntOrNull()
        when {
            intVal != null -> intVal
            value == "true" -> 1
            value == "false" -> 0
            value.isBlank() -> 0
            else -> 1
        }
    }
}

internal fun ValueExpression<*>.toBool() = when (this) {
    is IntExpression -> value != 0
    is BooleanExpression -> value
    is StringExpression -> toInt() == 1
}

internal sealed class ExpressionToken

internal sealed class VarDependentToken : ExpressionToken() {
    abstract fun eval(varContext: VarContextStack): ValueExpression<*>
}

internal class SingleVarToken(private val name: String) : VarDependentToken() {
    override fun eval(varContext: VarContextStack): ValueExpression<*>
            = varContext[name]?.varExpression() ?: failLex("Variable '$name' not set")
}

internal class GeneralDependantToken(private val expressions: List<ExpressionToken>) : VarDependentToken() {
    override fun eval(varContext: VarContextStack): ValueExpression<*> {
        val parts = expressions.map {
            when (it) {
                is VarDependentToken -> it.eval(varContext)
                is ConcreteToken -> it
            }
        }
        return collectionTraverser(parts).parseScope()
    }
}

internal class InterpolatedStringToken(
        private val producer: (VarContextStack) -> String
) : VarDependentToken() {
    override fun eval(varContext: VarContextStack) = StringExpression(producer(varContext))
}

internal sealed class ConcreteToken : ExpressionToken()

internal object OpenParen : ConcreteToken()
internal object CloseParen : ConcreteToken()

internal sealed class CalcExpr : ConcreteToken()

internal sealed class ValueExpression<T>(val value: T) : CalcExpr() {
    override fun toString() = value.toString()
}

internal class StringExpression(value: String) : ValueExpression<String>(value)
internal class IntExpression(value: Int) : ValueExpression<Int>(value)
internal class BooleanExpression(value: Boolean) : ValueExpression<Boolean>(value)

internal sealed class OperatorExpr(val priority: Int) : CalcExpr() {
    abstract fun calc(leftVal: ValueExpression<*>, rightVal: ValueExpression<*>): ValueExpression<*>
}

internal sealed class IntOperatorExpr(priority: Int, val calcFun: (Int, Int) -> ValueExpression<*>) : OperatorExpr(priority) {
    override fun calc(leftVal: ValueExpression<*>, rightVal: ValueExpression<*>)
            = calcFun(leftVal.toInt(), rightVal.toInt())
}

internal sealed class BoolOperatorExpr(priority: Int, val calcFun: (Boolean, Boolean) -> ValueExpression<*>) : OperatorExpr(priority) {
    override fun calc(leftVal: ValueExpression<*>, rightVal: ValueExpression<*>)
            = calcFun(leftVal.toBool(), rightVal.toBool())
}

internal object Mult : IntOperatorExpr(6, { a, b -> IntExpression(a * b) })
internal object Div : IntOperatorExpr(6, { a, b -> IntExpression(a / b) })
internal object Mod : IntOperatorExpr(6, { a, b -> IntExpression(a % b) })

internal object Plus : IntOperatorExpr(5, { a, b -> IntExpression(a + b) })
internal object Minus : IntOperatorExpr(5, { a, b -> IntExpression(a - b) })

internal object Less : IntOperatorExpr(4, { a, b -> BooleanExpression(a < b) })
internal object More : IntOperatorExpr(4, { a, b -> BooleanExpression(a > b) })
internal object LessEq : IntOperatorExpr(4, { a, b -> BooleanExpression(a <= b) })
internal object MoreEq : IntOperatorExpr(4, { a, b -> BooleanExpression(a >= b) })

internal object Eq : IntOperatorExpr(3, { a, b -> BooleanExpression(a == b) })
internal object NotEq : IntOperatorExpr(3, { a, b -> BooleanExpression(a != b) })

internal object And : BoolOperatorExpr(2, { a, b -> BooleanExpression(a && b) })
internal object Or : BoolOperatorExpr(1, { a, b -> BooleanExpression(a || b) })

internal object OnTrue : OperatorExpr(0) {
    override fun calc(leftVal: ValueExpression<*>, rightVal: ValueExpression<*>): ValueExpression<*>
        = if (leftVal.toBool()) rightVal else leftVal
}

internal object OnElse : OperatorExpr(0) {
    override fun calc(leftVal: ValueExpression<*>, rightVal: ValueExpression<*>): ValueExpression<*>
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
        val value: ValueExpression<*>,
        val node: ExprNode?
) {
    val singleVal: ValueExpression<*>?
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

internal fun BackingTraverser<ConcreteToken>.parseScope(): ValueExpression<*> {
    val levelValues = LinkedList<ValueExpression<*>>()
    val levelOps = LinkedList<OperatorExpr>()

    loop@while (hasNext()) {
        when (val token = next()) {
            is OpenParen -> {
                levelValues += parseScope()
            }
            is CloseParen -> {
                break@loop
            }
            is ValueExpression<*> -> {
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