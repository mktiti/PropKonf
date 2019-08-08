package com.mktiti.propkonf.core.general

fun <A, B, C> zip3(iterA: Iterable<A>, iterB: Iterable<B>, iterC: Iterable<C>): List<Triple<A, B, C>> =
        iterA.zip(iterB).zip(iterC) { (a, b), c -> Triple(a, b, c) }