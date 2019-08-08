package com.mktiti.propkonf.core.variable

import com.mktiti.propkonf.core.general.PropValue
import com.mktiti.propkonf.core.general.collectionTraverser
import org.junit.jupiter.api.Test

class EqualityPropertyIntegrationTest {

    companion object {
        private fun testInfixBool(a: PropValue<*>, op: OperatorExpr, b: PropValue<*>): Boolean {
            val tokens = listOf<ConcreteToken>(ValueExpression(a), op, ValueExpression(b))
            return collectionTraverser(tokens).parseScope().toBool()
        }

        private fun testEq(a: PropValue<*>, b: PropValue<*>) = testInfixBool(a, Eq, b)

        private fun testNotEq(a: PropValue<*>, b: PropValue<*>) = testInfixBool(a, NotEq, b)

        private fun equalityString(a: PropValue<*>, op: OperatorExpr, b: PropValue<*>) =
                "($a $op $b == ${testInfixBool(a, op, b)})"

        private fun testSymmetry(op: OperatorExpr) {
            allTestValues.cartesian(allTestValues).forEach { (a, b) ->
                assert(testInfixBool(a, op, b) == testInfixBool(b, op, a)) {
                    "Equality symmetry not satisfied: ${equalityString(a, op, b)}, but ${equalityString(b, op, a)}"
                }
            }
        }
    }

    @Test
    fun `test equality symmetry`() {
        testSymmetry(Eq)
    }

    @Test
    fun `test non-equality symmetry`() {
        testSymmetry(NotEq)
    }

    @Test
    fun `test equals non-equality exclusivity`() {
        allTestValues.cartesian(allTestValues).forEach { (a, b) ->
            assert(testEq(a, b) != testNotEq(a, b)) {
                "Equality / Non-Equality exclusivity not satisfied: ${equalityString(a, Eq, b)}, and also ${equalityString(b, NotEq, a)}"
            }
        }
    }

    @Test
    fun `test all equality transitivity`() {
        cartesian3(allTestValues, allTestValues, allTestValues).forEach { (a, b, c) ->
            if (testEq(a, b) && testEq(b, c)) {
                assert(testEq(a, c)) {
                    "Equality transitivity not satisfied: $a == $b && $b == $c but $a != $c"
                }
            }
        }
    }

    @Test
    fun `test all equality and non-equality transitivity`() {
        cartesian3(allTestValues, allTestValues, allTestValues).forEach { (a, b, c) ->
            if (testEq(a, b) && testNotEq(b, c)) {
                assert(testNotEq(a, c)) {
                    "Equality transitivity not satisfied: $a == $b && $b != $c but $a == $c"
                }
            }
        }
    }

}