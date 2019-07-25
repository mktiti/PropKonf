package com.mktiti.propkonf.core.config

import com.mktiti.propkonf.core.general.*
import java.io.PrintStream

private fun printIdent(ident: Int, target: PrintStream, identString: String) {
    target.print(identString.repeat(ident))
}

fun simplePropEscaped(property: SimpleProperty<*>) =
        "${property.name} = ${propertyEscaped(property.value)}"

fun propertyEscaped(property: PropValue<*>): String = when (property) {
    is StringVal -> stringEscaped(property.value)
    is IntVal -> property.value.toString()
    is TrueVal -> "true"
    is FalseVal -> "false"
}

fun stringEscaped(value: String): String {
    val builder = StringBuilder(value.length + 10)

    builder.append("\"")
    for (char in value) {
        when (char) {
            '\\' -> builder.append("""\\""")
            '\t' -> builder.append("""\t""")
            '\b' -> builder.append("""\b""")
            '\r' -> builder.append("""\r""")
            '\n' -> builder.append("""\n""")
            '"' -> builder.append("\\\"")
            '$' -> builder.append("\\$")
            else -> builder.append(char)
        }
    }
    builder.append("\"")

    return builder.toString()
}

fun Block.prettyPrintRoot(
        defaultIdent: Int = 0,
        target: PrintStream = System.out,
        identString: String = " ",
        identRepCount: Int = 4
) {
    prettyPrintVars(variables, defaultIdent, target, identString, identRepCount)
}

fun Block.prettyPrint(
        defaultIdent: Int = 0,
        target: PrintStream = System.out,
        identString: String = " ",
        identRepCount: Int = 4
) {
    printIdent(defaultIdent, target, identString)
    if (variables.isEmpty()) {
        target.println("$name {}")
    } else {
        target.println("$name {")
        prettyPrintVars(variables, defaultIdent + identRepCount, target, identString, identRepCount)
        printIdent(defaultIdent, target, identString)
        target.println("}")
    }
}

private fun prettyPrintVars(
        vars: List<Property>,
        ident: Int,
        target: PrintStream,
        identString: String,
        identRepCount: Int
) {
    for (variable in vars) {
        when (variable) {
            is SimpleProperty<*> -> {
                printIdent(ident, target, identString)
                target.println(simplePropEscaped(variable))
            }
            is Block -> variable.prettyPrint(ident, target, identString, identRepCount)
        }
    }
}

fun Block.linePrintRoot(target: PrintStream = System.out, lineBreak: Boolean = true) {
    variables.linePrintRoot(target)
    if (lineBreak) {
        target.println()
    }
}

private fun List<Property>.linePrintRoot(target: PrintStream = System.out) {
    forEach { variable ->
        when (variable) {
            is SimpleProperty<*> -> {
                target.print(simplePropEscaped(variable))
                target.print(" ")
            }
            is Block -> {
                variable.linePrint(target)
            }
        }
    }
}

private fun Block.linePrint(target: PrintStream = System.out) {
    target.print(name)
    target.print(" { ")
    variables.linePrintRoot(target)
    target.print("}")

}

fun Block.flatPrintRoot(target: PrintStream) {
    variables.flatPrint("", target)
}

private fun List<Property>.flatPrint(prefix: String, target: PrintStream) {
    forEach { variable ->
        when (variable) {
            is SimpleProperty<*> -> {
                target.print(prefix)
                target.println(variable)
            }
            is Block -> {
                variable.flatPrint(prefix, target)
            }
        }
    }
}

private fun Block.flatPrint(prefix: String, target: PrintStream) {
    variables.flatPrint("$prefix$name.", target)
}