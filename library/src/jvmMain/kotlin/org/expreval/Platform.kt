package org.expreval

import java.math.BigDecimal
import kotlin.math.abs

internal actual fun array(value: Any?): List<Any?>? =
    when {
        value is List<*> -> {
            value
        }

        value != null && value.javaClass.isArray -> {
            List(
                java.lang.reflect.Array
                    .getLength(value),
            ) {
                java.lang.reflect.Array
                    .get(value, it)
            }
        }

        else -> {
            null
        }
    }

internal actual fun radixNumber(digits: String, radix: Int): Double = digits.toBigInteger(radix).toDouble()
internal actual fun formatNumber(n: Double): String = BigDecimal.valueOf(n).stripTrailingZeros().let {
    if (abs(n) >= 1e-6 && abs(n) < 1e21) it.toPlainString() else it.toString().replace("E", "e")
}
internal actual fun shiftDecimal(n: Double, places: Int): Double = BigDecimal.valueOf(n).scaleByPowerOfTen(places).toDouble()
internal actual typealias SecurityException = java.lang.SecurityException

internal actual fun functionMember(value: ExpressionFunction, name: String): Any? = Undefined
