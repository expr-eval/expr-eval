package org.expreval

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

/** Represents a missing value, distinct from a Java null explicitly supplied in a scope. */
object Undefined {
    override fun toString() = "undefined"
}

internal fun fn(f: (Array<out Any?>) -> Any?) = ExpressionFunction { f(it) }
internal fun Array<out Any?>.arg(index: Int): Any? = if (index in indices) this[index] else Undefined
private fun Array<out Any?>.num(index: Int) = number(arg(index))

internal fun array(value: Any?): List<Any?>? = when {
    value is List<*> -> value
    value != null && value.javaClass.isArray ->
        List(java.lang.reflect.Array.getLength(value)) { java.lang.reflect.Array.get(value, it) }
    else -> null
}

internal fun truth(a: Any?): Boolean = when (a) {
    null, Undefined -> false
    is Boolean -> a
    is Number -> a.toDouble().let { it != 0.0 && !it.isNaN() }
    is String -> a.isNotEmpty()
    else -> true
}

internal fun jsString(a: Any?): String = when (a) {
    null -> "null"
    is String -> a
    is Boolean -> a.toString()
    is Number -> {
        val n = a.toDouble()
        when {
            n == 0.0 -> "0"
            !n.isFinite() -> n.toString()
            else -> BigDecimal.valueOf(n).stripTrailingZeros().let {
                if (abs(n) >= 1e-6 && abs(n) < 1e21) it.toPlainString() else it.toString().replace("E", "e")
            }
        }
    }
    is ExpressionFunction -> "function"
    else -> array(a)?.joinToString(",") { if (it == null || it === Undefined) "" else jsString(it) }
        ?: "[object Object]"
}

private val decimal = Regex("[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|Infinity)")
private fun parseNumber(s: String): Double {
    val text = s.trim { it.isWhitespace() || it == '\uFEFF' }
    if (text.isEmpty()) return 0.0
    val radix = when (text.take(2).lowercase()) {
        "0x" -> 16
        "0o" -> 8
        "0b" -> 2
        else -> 0
    }
    if (radix != 0) {
        val digits = text.drop(2)
        if (digits.isEmpty() || digits.any { it !in "0123456789abcdefABCDEF" || it.digitToIntOrNull(radix) == null }) return Double.NaN
        return digits.toBigInteger(radix).toDouble()
    }
    return if (decimal.matches(text)) text.toDoubleOrNull() ?: Double.NaN else Double.NaN
}

internal fun number(a: Any?): Double = when (a) {
    null -> 0.0
    is Number -> a.toDouble()
    is Boolean -> if (a) 1.0 else 0.0
    else -> parseNumber(jsString(a))
}

private fun stringPrimitive(x: Any?): Any? =
    if (x == null || x === Undefined || x is String || x is Boolean || x is Number) x else jsString(x)

private fun relation(a: Any?, b: Any?, string: (Int) -> Boolean, numeric: (Double, Double) -> Boolean): Boolean {
    val left = stringPrimitive(a)
    val right = stringPrimitive(b)
    return if (left is String && right is String) string(left.compareTo(right)) else numeric(number(left), number(right))
}

private fun equal(a: Any?, b: Any?, matchNaN: Boolean = false): Boolean = when {
    a is Number && b is Number -> {
        val x = a.toDouble()
        val y = b.toDouble()
        x == y || (matchNaN && x.isNaN() && y.isNaN())
    }
    a is String && b is String -> a == b
    a is Boolean && b is Boolean -> a == b
    else -> a === b
}

private fun int32(value: Any?): Int {
    val n = number(value)
    if (!n.isFinite()) return 0
    return ((if (n < 0) ceil(n) else floor(n)) % 4294967296.0).toLong().toInt()
}

private fun power(base: Double, exponent: Double): Double = when {
    exponent.isNaN() || (abs(base) == 1.0 && exponent.isInfinite()) -> Double.NaN
    else -> base.pow(exponent)
}

private fun jsRound(n: Double): Double = when {
    n == 0.0 || !n.isFinite() -> n
    n >= -0.5 && n < 0.0 -> -0.0
    else -> floor(n).let { if (n - it < 0.5) it else it + 1 }
}

internal fun unaryBuiltins(): MutableMap<String, ExpressionFunction> {
    val math = mapOf<String, (Double) -> Double>(
        "sin" to ::sin, "cos" to ::cos, "tan" to ::tan, "asin" to ::asin, "acos" to ::acos,
        "atan" to ::atan, "sinh" to ::sinh, "cosh" to ::cosh, "tanh" to ::tanh, "asinh" to ::asinh,
        "acosh" to ::acosh, "atanh" to ::atanh, "sqrt" to ::sqrt, "cbrt" to Math::cbrt,
        "log" to ::ln, "ln" to ::ln, "lg" to ::log10, "log10" to ::log10, "log2" to ::log2,
        "expm1" to ::expm1, "log1p" to ::ln1p, "exp" to ::exp, "abs" to ::abs, "ceil" to ::ceil,
        "floor" to ::floor, "round" to ::jsRound, "trunc" to { if (it < 0) ceil(it) else floor(it) },
        "-" to { -it }, "+" to { it }, "sign" to ::sign,
    )
    return math.mapValuesTo(linkedMapOf()) { (_, f) -> fn { f(it.num(0)) } }.apply {
        put("not", fn { !truth(it.arg(0)) })
        put("length", fn { (array(it.arg(0))?.size ?: jsString(it.arg(0)).length).toDouble() })
        put("!", fn { factorial(it.arg(0)) })
    }
}

internal fun binaryBuiltins(): MutableMap<String, ExpressionFunction> = linkedMapOf(
    "+" to fn { it.num(0) + it.num(1) },
    "-" to fn { it.num(0) - it.num(1) },
    "*" to fn { it.num(0) * it.num(1) },
    "/" to fn { it.num(0) / it.num(1) },
    "%" to fn { it.num(0) % it.num(1) },
    "^" to fn { power(it.num(0), it.num(1)) },
    "||" to fn {
        val a = array(it.arg(0))
        val b = array(it.arg(1))
        if (a != null && b != null) a + b else jsString(it.arg(0)) + jsString(it.arg(1))
    },
    "==" to fn { equal(it.arg(0), it.arg(1)) },
    "!=" to fn { !equal(it.arg(0), it.arg(1)) },
    "<" to fn { relation(it.arg(0), it.arg(1), { c -> c < 0 }, { a, b -> a < b }) },
    ">" to fn { relation(it.arg(0), it.arg(1), { c -> c > 0 }, { a, b -> a > b }) },
    "<=" to fn { relation(it.arg(0), it.arg(1), { c -> c <= 0 }, { a, b -> a <= b }) },
    ">=" to fn { relation(it.arg(0), it.arg(1), { c -> c >= 0 }, { a, b -> a >= b }) },
    "and" to fn { truth(it.arg(0)) && truth(it.arg(1)) },
    "or" to fn { truth(it.arg(0)) || truth(it.arg(1)) },
    "in" to fn {
        val collection = it.arg(1)
        if (collection is String) collection.contains(jsString(it.arg(0)))
        else requireArray(collection, "Right operand of in").any { value -> equal(it.arg(0), value, true) }
    },
    "=" to fn {
        val value = it.arg(1)
        @Suppress("UNCHECKED_CAST")
        (it.arg(2) as? MutableMap<String, Any?>)?.set(jsString(it.arg(0)), value)
        value
    },
    "[" to fn { property(it.arg(0), int32(it.arg(1)).toString()) },
)

internal fun property(value: Any?, name: String): Any? {
    if (value == null || value === Undefined) throw IllegalArgumentException("Cannot read property $name of $value")
    if (value is Map<*, *>) return if (value.containsKey(name)) value[name] else Undefined
    val array = array(value)
    if (name == "length") {
        if (array != null) return array.size.toDouble()
        if (value is String) return value.length.toDouble()
    }
    val index = name.toIntOrNull()?.takeIf { it.toString() == name } ?: return Undefined
    if (array != null) return if (index in array.indices) array[index] else Undefined
    if (value is String) return value.getOrNull(index)?.toString() ?: Undefined
    return Undefined
}

private fun requireArray(value: Any?, context: String) =
    array(value) ?: throw IllegalArgumentException("$context is not an array")

private fun callback(args: Array<out Any?>, name: String) =
    args.arg(0) as? ExpressionFunction ?: throw IllegalArgumentException("First argument to $name is not a function")

internal fun functionBuiltins(): MutableMap<String, ExpressionFunction> {
    val hypot = fn { args ->
        val values = args.map { abs(number(it)) }
        val largest = values.maxOfOrNull { if (it.isNaN()) 0.0 else it } ?: 0.0
        when {
            largest.isInfinite() -> Double.POSITIVE_INFINITY
            values.any(Double::isNaN) -> Double.NaN
            largest == 0.0 -> 0.0
            else -> largest * sqrt(values.sumOf { (it / largest).pow(2) })
        }
    }
    return linkedMapOf(
        "random" to fn { Math.random() * if (truth(it.arg(0))) it.num(0) else 1.0 },
        "fac" to fn { factorial(it.arg(0)) },
        "gamma" to fn { gamma(it.num(0)) },
        "min" to fn { extrema(it, true) },
        "max" to fn { extrema(it, false) },
        "hypot" to hypot, "pyt" to hypot,
        "pow" to fn { power(it.num(0), it.num(1)) },
        "atan2" to fn { atan2(it.num(0), it.num(1)) },
        "if" to fn { if (truth(it.arg(0))) it.arg(1) else it.arg(2) },
        "roundTo" to fn { roundTo(it.num(0), if (it.size > 1) it.num(1) else 0.0) },
        "map" to fn { args ->
            val f = callback(args, "map")
            requireArray(args.arg(1), "Second argument to map").mapIndexed { i, a -> f.call(a, i.toDouble()) }
        },
        "fold" to fn { args ->
            val f = callback(args, "fold")
            requireArray(args.arg(2), "Second argument to fold").foldIndexed(args.arg(1)) { i, acc, a -> f.call(acc, a, i.toDouble()) }
        },
        "filter" to fn { args ->
            val f = callback(args, "filter")
            requireArray(args.arg(1), "Second argument to filter").filterIndexed { i, a -> truth(f.call(a, i.toDouble())) }
        },
        "indexOf" to fn {
            val sequence = it.arg(1)
            (if (sequence is String) sequence.indexOf(jsString(it.arg(0)))
            else requireArray(sequence, "Second argument to indexOf (expected string or array)")
                .indexOfFirst { value -> equal(it.arg(0), value) }).toDouble()
        },
        "join" to fn {
            requireArray(it.arg(1), "Second argument to join")
                .joinToString(if (it.arg(0) === Undefined) "," else jsString(it.arg(0))) { value ->
                    if (value == null || value === Undefined) "" else jsString(value)
                }
        },
        "sum" to fn { requireArray(it.arg(0), "Sum argument").sumOf(::number) },
    )
}

private fun extrema(args: Array<out Any?>, minimum: Boolean): Any? {
    val start = if (minimum) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
    val values = array(args.arg(0)) ?: when {
        args.arg(0) === Undefined -> return start
        args.size == 1 -> return args[0]
        else -> args.asList()
    }
    return values.fold(start) { result, value -> if (minimum) min(result, number(value)) else max(result, number(value)) }
}

private fun roundTo(n: Double, places: Double): Double {
    if (places == 0.0) return jsRound(n)
    if (!n.isFinite() || places % 1 != 0.0) return Double.NaN
    if (n == 0.0 || places < -324) return 0.0
    if (places > 632) return Double.NaN
    val shifted = BigDecimal.valueOf(n).scaleByPowerOfTen(places.toInt()).toDouble()
    if (!shifted.isFinite()) return Double.NaN
    return BigDecimal.valueOf(jsRound(shifted)).setScale(0, RoundingMode.UNNECESSARY)
        .scaleByPowerOfTen(-places.toInt()).toDouble()
}

private fun factorial(a: Any?): Double {
    val v = stringPrimitive(a)
    return gamma(if (v is String) number(v + "1") else number(v) + 1)
}

private val gammaP = doubleArrayOf(
    0.99999999999999709182, 57.156235665862923517, -59.597960355475491248,
    14.136097974741747174, -0.49191381609762019978, 0.33994649984811888699e-4,
    0.46523628927048575665e-4, -0.98374475304879564677e-4,
    0.15808870322491248884e-3, -0.21026444172410488319e-3,
    0.2174396181152126432e-3, -0.16431810653676389022e-3,
    0.84418223983852743293e-4, -0.2619083840158140867e-4, 0.36899182659531622704e-5,
)

private fun gamma(n: Double): Double {
    if (n.isFinite() && n == floor(n)) {
        if (n <= 0 || n > 171) return Double.POSITIVE_INFINITY
        var result = 1.0
        for (i in (n.toInt() - 1) downTo 2) result *= i
        return result
    }
    if (n < 0.5) return PI / (sin(PI * n) * gamma(1 - n))
    if (n >= 171.35) return Double.POSITIVE_INFINITY
    if (n > 85) return sqrt(2 * PI / n) * (n / E).pow(n) * (
        1 + 1 / (12 * n) + 1 / (288 * n.pow(2)) - 139 / (51840 * n.pow(3)) -
            571 / (2488320 * n.pow(4)) + 163879 / (209018880 * n.pow(5)) +
            5246819 / (75246796800.0 * n.pow(6))
        )
    val k = n - 1
    var x = gammaP[0]
    for (i in 1 until gammaP.size) x += gammaP[i] / (k + i)
    val t = k + 4.7421875 + 0.5
    return sqrt(2 * PI) * t.pow(k + 0.5) * exp(-t) * x
}
