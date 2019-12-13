package com.mktiti.propkonf.core.api

import com.mktiti.propkonf.core.config.parseBlock
import com.mktiti.propkonf.core.config.tokenize
import com.mktiti.propkonf.core.general.BackingIterator
import com.mktiti.propkonf.core.general.StringVal
import com.mktiti.propkonf.core.general.loadFile
import com.mktiti.propkonf.core.variable.MutableVarContextStack
import com.mktiti.propkonf.core.variable.VarContextStack
import java.nio.file.Path
import java.nio.file.Paths

class ConfigFormatException(
        message: String,
        cause: Exception? = null
) : RuntimeException(message, cause)

fun fileConfig(path: String, rootVars: VarContextStack? = null, passEnvVars: Boolean = false): PropStore = fileConfig(Paths.get(path), rootVars, passEnvVars)

fun fileConfig(path: Path, rootVars: VarContextStack? = null, passEnvVars: Boolean = false): PropStore {
    val trueRoot: VarContextStack = if (passEnvVars) {
        MutableVarContextStack(rootVars).apply {
            if (passEnvVars) {
                System.getenv().forEach { (name, value) ->
                    this[name] = StringVal(value)
                }
            }
        }
    } else {
        rootVars ?: VarContextStack(null)
    }

    return try {
        BlockPropStore(
            block = BackingIterator(loadFile(path).tokenize()).parseBlock("", null).evaluate(trueRoot)
        )
    } catch (e: Exception) {
        throw ConfigFormatException("Failed to parse config: $path", e)
    }
}