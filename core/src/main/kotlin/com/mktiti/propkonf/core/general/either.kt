package com.mktiti.propkonf.core.general

sealed class Either<L, R> {

    abstract fun <NL, NR> map(
            onLeft: (L) -> NL,
            onRight: (R) -> NR
    ): Either<NL, NR>

    fun <NL> mapLeft(onLeft: (L) -> NL): Either<NL, R> = map(onLeft, { it })

    fun <NR> mapRight(onRight: (R) -> NR): Either<L, NR> = map({ it }, onRight)

    abstract fun <T> unify(
            onLeft: (L) -> T,
            onRight: (R) -> T
    ): T

    fun unifyLeft(
            onLeft: (L) -> R
    ): R = unify(onLeft, { it })

    fun unifyRight(
            onRight: (R) -> L
    ): L = unify({ it }, onRight)

}

fun <V> Either<out V, out V>.value(): V = when (this) {
    is Left -> value
    is Right -> value
}

class Left<L, R>(
        val value: L
) : Either<L, R>() {

    override fun <NL, NR> map(onLeft: (L) -> NL, onRight: (R) -> NR): Left<NL, NR> = Left(onLeft(value))

    override fun <T> unify(onLeft: (L) -> T, onRight: (R) -> T): T = onLeft(value)
}

class Right<L, R>(
        val value: R
) : Either<L, R>() {

    override fun <NL, NR> map(onLeft: (L) -> NL, onRight: (R) -> NR): Right<NL, NR> = Right(onRight(value))

    override fun <T> unify(onLeft: (L) -> T, onRight: (R) -> T): T = onRight(value)
}