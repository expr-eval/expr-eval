package org.expreval

internal actual fun array(value: Any?): List<Any?>? = when (value) {
    is List<*> -> value
    is Array<*> -> value.asList()
    else -> null
}
internal actual fun radixNumber(digits: String, radix: Int): Double = js("parseInt(digits, radix)")
internal actual fun formatNumber(n: Double): String = n.toString()
internal actual fun shiftDecimal(n: Double, places: Int): Double {
    val parts = n.toString().split("e")
    return (parts[0] + "e" + ((parts.getOrNull(1)?.toInt() ?: 0) + places)).toDouble()
}
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("SecurityError")
internal actual class SecurityException actual constructor(message: String) : RuntimeException(message)

@OptIn(ExperimentalJsExport::class)
@JsExport
fun securityErrorType(): dynamic = SecurityException::class.js

internal actual fun functionMember(value: ExpressionFunction, name: String): Any? = fromJs(toJs(value)[name])
