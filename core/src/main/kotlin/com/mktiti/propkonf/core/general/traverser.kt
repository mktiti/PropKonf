package com.mktiti.propkonf.core.general

import java.lang.Integer.max

interface TraverserStore<T> {

    val size: Int

    operator fun get(index: Int): T

}

fun <T> collectionTraverser(collection: Collection<T>)
        = BackingTraverser(collectionToStore(collection))

fun <T> collectionToStore(collection: Collection<T>) = object : TraverserStore<T> {

    val data = ArrayList<T>(collection)

    override val size: Int
        get() = data.size

    override fun get(index: Int): T  = data[index]

}

private fun charArrayToStore(array: CharArray) = object : TraverserStore<Char> {

    override val size: Int
        get() = array.size

    override fun get(index: Int): Char  = array[index]

}

fun stringTraverser(array: String): SourceStream
        = BackingTraverser(charArrayToStore(array.toCharArray()))

typealias SourceStream = BackingTraverser<Char>

class BackingTraverser<T>(private val store: TraverserStore<T>) {

    private var index = 0

    val remaining: Int
        get() = store.size - index

    constructor(data: Collection<T>) : this(collectionToStore(data))

    fun safeNext(): T? = if (hasNext()) store[index++] else null

    // Prevent auto boxing
    fun next(): T = if (hasNext()) store[index++] else throw IllegalStateException("Traverser is finished")

    fun safePeek(): T? = if (hasNext()) store[index] else null

    // Prevent auto boxing
    fun peek(): T = if (hasNext()) store[index] else throw IllegalStateException("Traverser is finished")

    fun back() {
        index = max(0, index - 1)
    }

    fun reset() {
        index = 0
    }

    fun hasNext() = index < store.size

}