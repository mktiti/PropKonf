package com.mktiti.propkonf.core.variable

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class MinusTest {

    @Test
    fun `test simple`() {
        assertThat(calculateInt("70 - 30")).isEqualTo(40)
    }

    @Test
    fun `test no whitespace`() {
        assertThat(calculateInt("70-30")).isEqualTo(40)
    }

    @Test
    fun `test non-space whitespace`() {
        assertThat(calculateInt("70\t-\n30")).isEqualTo(40)
    }

    @Test
    fun `test missing arg`() {
        assertThatThrownBy {
            calculateInt("70 -")
        }.isInstanceOf(ExpressionEvalException::class.java)
    }

    @Test
    fun `test missing arg no whitespace`() {
        assertThatThrownBy {
            calculateInt("70-")
        }.isInstanceOf(ExpressionEvalException::class.java)
    }

    @Test
    fun `test unary negative`() {
        assertThat(calculateInt("70 - - 30")).isEqualTo(100)
    }

    @Test
    fun `test unary negative no whitespace`() {
        assertThat(calculateInt("70--30")).isEqualTo(100)
    }

    @Test
    fun `test unary positive`() {
        assertThat(calculateInt("70 - +30")).isEqualTo(40)
    }

    @Test
    fun `test unary positive no whitespace`() {
        assertThat(calculateInt("70-+30")).isEqualTo(40)
    }

    @Test
    fun `test negatives`() {
        assertThat(calculateInt("-70 - -30")).isEqualTo(-40)
    }

}