
fun launchScript(script: String) {
    println("Running script: $script")
}

enum class CacheStrategy(private val parse: String) {
    LRU("lru"), TLRU("tlru"), MRU("mru"), RR("rr");

    companion object {
        fun parse(name: String): CacheStrategy = values().find {
            it.parse == name
        }!!
    }
}

class Server(
    webPort: Int,
    consolePort: Int,
    logPort: Int
) {

    init {
        println("""Running server mock
            |==== Web port: $webPort
            |==== Console port: $consolePort
            |==== Log port: $logPort
        """.trimIndent())
    }

    fun enableCache(location: String, strategy: CacheStrategy) {
        println("Cache start @ $location, strategy: $strategy")
    }

}
