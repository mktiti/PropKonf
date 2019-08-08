package com.mktiti.propkonf.core.variable

import com.mktiti.propkonf.core.general.*

private val stringTestValues = listOf(
        "", " ", "\n", "abc", "bca", "123", "true", "false"
)

private val intTestValues = listOf(
        0, 1, -1, 123, 321
)

internal val allTestValues: List<PropValue<*>>
        = stringTestValues.map(::StringVal) +
          intTestValues.map(::IntVal) +
          TrueVal +  FalseVal


fun <A, B> Iterable<A>.cartesian(other: Iterable<B>): Sequence<Pair<A, B>> =
    asSequence().flatMap { a ->
        other.asSequence().map { b -> a to b }
    }

fun <A, B, C> cartesian3(iterA: Iterable<A>, iterB: Iterable<B>, iterC: Iterable<C>): Sequence<Triple<A, B, C>> =
    iterA.asSequence().flatMap { a ->
        iterB.asSequence().flatMap { b ->
            iterC.asSequence().map { c -> Triple(a, b, c) }
        }
    }