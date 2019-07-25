@file:JvmName("MainKt")

package hu.mktiti.propkonf.tools

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import hu.mktiti.propkonf.core.config.flatPrintRoot
import hu.mktiti.propkonf.core.config.fullParse
import hu.mktiti.propkonf.core.config.linePrintRoot
import hu.mktiti.propkonf.core.config.prettyPrintRoot
import hu.mktiti.propkonf.core.general.StringVal
import hu.mktiti.propkonf.core.variable.MutableVarContextStack
import hu.mktiti.propkonf.tools.generated.ProjectInfo
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Paths

enum class PrintFormat {
    PRETTY, ONE_LINE, FLAT
}

class Arguments(parser: ArgParser) {

    val version by parser.flagging("-v", "--version", help = "Prints the version")

    val sourcePath by parser.storing("-s", "--source-file", help = "Source file path to use, use '-' for stdin, defaults to stdin").default("-")

    val targetPath by parser.storing("-o", "--output-file", help = "Target file path to print, use '-' for stdout, defaults to stdout").default("-")

    val printFormat by parser.storing("-f", "--format", help = "Output format, either 'pretty', 'one-line', or 'flat', defaults to pretty printing") {
        when (this.toLowerCase()) {
            "pretty" -> PrintFormat.PRETTY
            "one-line" -> PrintFormat.ONE_LINE
            "flat" -> PrintFormat.FLAT
            else -> throw SystemExitException("Invalid output format, must be either 'pretty', 'one-line', or 'flat'", 1)
        }
    }.default(PrintFormat.PRETTY)

    val passEnvVars by parser.flagging("-e", "--pass-environment", help = "Pass the environment variables as root variables").default(false)

    val transformEnvVars by parser.flagging("-t", "--transform-environment", help = "Transform the passed environment variables to snake-case (MY_VAR -> my-var)")

    val rootVars by parser.storing("-r", "--root-vars",
            help = "Comma separated list of extra root variables, quotes around string values are not needed. e.g. foo=5,bar=value,baz=true") {
            split(',').map {
                val split = it.split('=')
                if (split.size != 2) {
                    throw SystemExitException("Root variables contain bad assignment ($it)", 1)
                }

                split[0] to split[1]
            }
    }.default(emptyList())

}

private fun toSnakeCase(name: String) =
    String(name.map { char ->
        if (char == '_') '-' else char.toLowerCase()
    }.toCharArray())

fun main(args: Array<String>) = mainBody {
    ArgParser(args).parseInto(::Arguments).run {
        if (version) {
            println("PropKonf Tools Version ${ProjectInfo.version}")
            return@mainBody
        }

        val envVars = MutableVarContextStack().apply {
            if (passEnvVars) {
                System.getenv().forEach { (name, value) ->
                    val newName = if (transformEnvVars) toSnakeCase(name) else name
                    this[newName] = StringVal(value)
                }
            }
        }

        val customVars = MutableVarContextStack(envVars).apply {
            rootVars.forEach { (name, value) ->
                this[name] = StringVal(value)
            }
        }

        val block = if (sourcePath == "-") {
            val content = generateSequence(::readLine).joinToString("\n")
            fullParse(content, customVars)
        } else {
            fullParse(Paths.get(sourcePath), customVars)
        }

        if (block == null) {
            System.exit(2)
            return@mainBody
        }

        val target: ((PrintStream) -> Unit) -> Unit = if (targetPath == "-") {
            {
                code -> PrintStream(System.out).use(code)
            }
        } else {
            { code ->
                FileOutputStream(Paths.get(targetPath).toFile()).use { outStream ->
                    PrintStream(outStream).use(code)
                }
            }
        }

        target { printStream ->
            when (printFormat) {
                PrintFormat.PRETTY -> block.prettyPrintRoot(target = printStream)
                PrintFormat.ONE_LINE -> block.linePrintRoot(target = printStream)
                PrintFormat.FLAT -> block.flatPrintRoot(target = printStream)
            }
        }
    }
}