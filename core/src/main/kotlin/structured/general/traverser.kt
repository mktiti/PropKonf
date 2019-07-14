package structured.general

import java.lang.Integer.max
import java.nio.file.Path

fun loadFile(path: Path): SourceStream {
    val data = path.toFile().readLines()
            .map { it.trimStart() }
            .filterNot { it.startsWith("//") }

    val size = max(data.foldRight(-1) { s, acc -> s.length + 1 + acc }, 0)
    val builder = data.joinTo(StringBuilder(size), separator = "\n")

    return stringTraverser(builder.toString())
}

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

    fun next(): T = safeNext() ?: throw IllegalStateException("Traverser is finished")

    fun safePeek(): T? = if (hasNext()) store[index] else null

    fun peek(): T = safePeek() ?: throw IllegalStateException("Traverser is finished")

    fun back() {
        index = max(0, index - 1)
    }

    fun reset() {
        index = 0
    }

    fun hasNext() = index < store.size

}