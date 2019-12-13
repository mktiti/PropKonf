import com.mktiti.propkonf.core.api.fileConfig
import com.mktiti.propkonf.core.variable.buildVarContext
import java.util.*

// See demo.conf in resources (demo/src/main/resources/demo.conf)
// Try running with PORT environment variable set to an integer
fun main() {

    val externalVars = buildVarContext {
        if (Random().nextBoolean()) {
            // See cache location change on rerun ...probably
            string("external-loc", "random/path")
        }
    }

    val demoPath = object {}.javaClass.getResource("/demo.conf").path

    val config = fileConfig(demoPath, externalVars, passEnvVars = true)
    try {
        launchScript(script = config["script.init"])
        val server = Server(
                webPort = config.int("server.web-port"),
                consolePort = config.int("server.console-port"),
                logPort = config.intOpt("server.log-port") ?: 1000
        )

        println(config["server.needs-root-message"])

        val serverConf = config.view("server.cache") // Who likes repeating themselves
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
    println("  ==== x: ${config.int("demo.inner.useless.x")}")
    println("  ==== b: ${config.bool("demo.inner.useless.b")}")

}
