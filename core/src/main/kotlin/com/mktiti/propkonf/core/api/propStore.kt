package com.mktiti.propkonf.core.api

import com.mktiti.propkonf.core.config.Block
import com.mktiti.propkonf.core.general.*
import com.mktiti.propkonf.core.general.FalseVal
import com.mktiti.propkonf.core.general.TrueVal

interface PropStore {

    fun opt(key: String, convertIfNeeded: Boolean = true): String?

    operator fun get(key: String, convertIfNeeded: Boolean = true): String = opt(key, convertIfNeeded)!!

    fun intOpt(key: String, convertIfNeeded: Boolean = true): Int?

    fun int(key: String, convertIfNeeded: Boolean = true): Int = intOpt(key, convertIfNeeded)!!

    fun boolOpt(key: String, convertIfNeeded: Boolean = true): Boolean?

    fun bool(key: String, convertIfNeeded: Boolean = true): Boolean = boolOpt(key, convertIfNeeded)!!

    fun <T> getOpt(key: String, transformer: (String) -> T): T?

    fun <T> get(key: String, transformer: (String) -> T): T = getOpt(key, transformer)!!

    fun viewOpt(key: String): PropStore?

    fun viewSafe(key: String): PropStore

    fun view(key: String): PropStore = viewOpt(key)!!

}

class BlockPropStore(private val block: Block) : PropStore {

    private fun keyParts(key: String) = key.split(".")

    private fun convertString(value: PropValue<*>?, convert: Boolean): String? = when {
        convert -> {
            when (value) {
                is StringVal -> value.value
                is IntVal -> value.value.toString()
                TrueVal -> "true"
                FalseVal -> "false"
                null -> null
            }
        }
        value is StringVal -> value.value
        else -> null
    }

    override fun opt(key: String, convertIfNeeded: Boolean): String? {
        return when (val value = block[keyParts(key)]) {
            null -> null
            else -> convertString(value, convertIfNeeded)
        }
    }

    override fun <T> getOpt(key: String, transformer: (String) -> T): T? = opt(key, true)?.let(transformer)

    override fun intOpt(key: String, convertIfNeeded: Boolean): Int? {
        return when (val value = block[keyParts(key)]) {
            is IntVal -> value.value
            null -> null
            else -> if (convertIfNeeded) {
                when (value) {
                    is StringVal -> Integer.parseInt(value.value)
                    TrueVal -> 1
                    FalseVal -> 0
                    else -> null
                }
            } else {
                null
            }
        }
    }

    override fun boolOpt(key: String, convertIfNeeded: Boolean): Boolean? {
        return when (val value = block[keyParts(key)]) {
            is BoolVal -> value.value
            null -> null
            else -> if (convertIfNeeded) {
                when (value) {
                    is StringVal -> when (value.value) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                    is IntVal -> value.value != 0
                    else -> null
                }
            } else {
                null
            }
        }
    }

    override fun viewOpt(key: String): PropStore? = block.view(keyParts(key))?.let { BlockPropStore(it) }

    override fun viewSafe(key: String): PropStore = viewOpt(key) ?: BlockPropStore(Block("", emptyList()))

}