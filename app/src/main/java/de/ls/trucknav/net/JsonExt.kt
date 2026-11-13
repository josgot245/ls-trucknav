package de.ls.trucknav.net

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

internal fun JsonObject.stringOrNull(key: String): String? {
    val value = this[key]
    if (value == null || value is JsonNull) return null
    val primitive = value as? JsonPrimitive ?: return null
    return primitive.contentOrNull?.takeIf { it.isNotBlank() }
}

internal fun JsonObject.doubleOrNull(key: String): Double? {
    val value = this[key]
    if (value == null || value is JsonNull) return null
    return (value as? JsonPrimitive)?.doubleOrNull
}

internal fun JsonObject.intOrNull(key: String): Int? {
    val value = this[key]
    if (value == null || value is JsonNull) return null
    return (value as? JsonPrimitive)?.intOrNull
}
