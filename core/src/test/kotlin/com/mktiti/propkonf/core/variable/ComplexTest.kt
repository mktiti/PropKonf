package com.mktiti.propkonf.core.variable

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ComplexTest {

    @Test
    fun `test deeply nested parens`() {
        assertThat(calculateInt("0+(1+(2+(3+(4+5)+6)+7)+8)+9")).isEqualTo(45)
    }

    @Test
    fun `test deeply nested string expressions`() {
        assertThat(calculateInt("1 + \"\${2 + \"\${3}\"}\" ")).isEqualTo(6)
    }

    @Test
    fun `test operator reverse order`() {
        assertThat(calculateInt("10 - 5 + 2 / 2 * 3 % 2")).isEqualTo(6)
        assertThat(calculateBool("true !=  6 == 10 - 5 + 2 / 2 * 3 % 2")).isEqualTo(false)
        assertThat(calculateBool("true && true || true !=  6 == 10 - 5 + 2 / 2 * 3 % 2")).isEqualTo(true)
    }

}