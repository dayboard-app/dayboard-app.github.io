package io.github.dayboard.data.firebase

/**
 * Turns a value that came out of Firestore into ordinary Kotlin collections.
 *
 * This exists because of one mismatch that is easy to miss: a JS array arrives in
 * Kotlin as an `Array`, which is **not** a `List`. Code in `:shared` that tests
 * `value as? List<*>` therefore sees null for every stored array and quietly falls
 * back to its defaults - a saved layout would read as no layout at all, with no
 * error anywhere.
 *
 * Converting once at the boundary keeps every parser above it plain Kotlin, and
 * therefore unit-testable without a browser.
 */
internal fun jsToKotlin(value: dynamic): Any? = when {
    value == null || value == undefined -> null

    // Arrays before objects: a JS array is an object too, and the object branch
    // would turn it into a map keyed by "0", "1", "2".
    js("Array.isArray")(value) as Boolean ->
        (value as Array<dynamic>).map { jsToKotlin(it) }

    jsTypeOf(value) == "object" ->
        objectKeys(value).associateWith { jsToKotlin(value[it]) }

    // Strings, numbers and booleans already are what Kotlin expects. Firestore
    // Timestamps fall here too and stay opaque, which is fine: nothing reads them.
    else -> value
}

/** Reads a stored map field, or null when it is absent or not a map. */
@Suppress("UNCHECKED_CAST")
internal fun dynamicToMap(value: dynamic): Map<String, Any?>? =
    jsToKotlin(value) as? Map<String, Any?>

private fun objectKeys(value: dynamic): Array<String> =
    js("Object.keys")(value).unsafeCast<Array<String>>()

/** Builds an empty JS object to fill in, since Kotlin has no object literal. */
internal fun jsObject(): dynamic = js("{}")
