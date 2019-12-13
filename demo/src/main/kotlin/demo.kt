import com.mktiti.propkonf.core.api.fileConfig

// See demo.conf in resources (demo/src/main/resources/demo.conf)
// Try running with PORT environment variable set to an integer
fun main() {

    val config = fileConfig(object {}.javaClass.getResource("/demo.conf").path, passEnvVars = true)
    try {
        launchScript(script = config["script.init"])
        val server = Server(
                webPort = config.int("server.web-port"),
                consolePort = config.int("server.console-port"),
                logPort = config.intOpt("server.log-port") ?: 1000
        )

        println(config["server.needs-root-message"])

        val serverConf = config.view("server.cache") // Who likes typing
        if (serverConf.boolOpt("enabled") == true) {
            server.enableCache(
                    location = serverConf["location"], // Same as config["server.cache.location"]
                    strategy = serverConf.get("strategy") { CacheStrategy.parse(it) }
            )
        }

    } finally {
        launchScript(script = config["script.stop"])
    }

    println("Some more demo")
    println("==== x: ${config.int("demo.inner.useless.x")}")
    println("==== b: ${config.bool("demo.inner.useless.b")}")

}
