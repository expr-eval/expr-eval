package org.expreval

// Preserve identity in both directions, including callbacks registered under several names.
// Weak keys let the JS runtime collect scopes and callback wrappers together.
private val fromJsCache: dynamic = js("new WeakMap()")
private val toJsCache: dynamic = js("new WeakMap()")
internal fun keys(obj: dynamic): Array<String> = js("Object.keys(obj)")
internal fun plainObject(): dynamic = js("({})")
private fun owns(obj: dynamic, key: String): Boolean = js("Object.prototype.hasOwnProperty.call(obj, key)")
internal fun defaultObject(value: dynamic): dynamic = value ?: plainObject()

internal class ObjectMap<V>(
    val obj: dynamic,
    private val decode: (dynamic) -> V,
    private val encode: (V) -> dynamic,
) : AbstractMutableMap<String, V>(), MemberSource {
    override fun member(name: String): Any? = fromJs(obj[name])
    override fun containsKey(key: String): Boolean = owns(obj, key)
    override fun get(key: String): V? = if (containsKey(key)) decode(obj[key]) else null
    override fun put(key: String, value: V): V? {
        val previous = get(key)
        val native = encode(value)
        val target = obj
        target[key] = native
        return previous
    }
    override val entries: MutableSet<MutableMap.MutableEntry<String, V>>
        get() = keys(obj).associateWithTo(linkedMapOf()) { decode(obj[it]) }.entries
}

private class NativeArray(private val obj: dynamic) : AbstractList<Any?>() {
    override val size: Int get() = obj.length
    override fun get(index: Int): Any? = fromJs(obj[index])
}

private class NativeFunction(private val func: dynamic) : ExpressionFunction {
    override fun call(vararg args: Any?): Any? = callWithReceiver(Undefined, *args)

    override fun callWithReceiver(receiver: Any?, vararg args: Any?): Any? =
        fromJs(func.apply(toJs(receiver), args.map(::toJs).toTypedArray()))
}

internal fun scopeMap(obj: dynamic): MutableMap<String, Any?> = ObjectMap(defaultObject(obj), ::fromJs, ::toJs)

internal fun fromJs(value: dynamic): Any? {
    if (jsTypeOf(value) == "undefined") return Undefined
    if (value == null || jsTypeOf(value) != "object" && jsTypeOf(value) != "function") return value
    if (fromJsCache.has(value) as Boolean) return fromJsCache.get(value)
    val wrapped: Any = when {
        jsTypeOf(value) == "function" -> NativeFunction(value)
        js("Array.isArray(value)") as Boolean -> NativeArray(value)
        else -> scopeMap(value)
    }
    fromJsCache.set(value, wrapped)
    toJsCache.set(wrapped, value)
    return wrapped
}

internal fun toJs(value: Any?): dynamic {
    if (value === Undefined) return js("undefined")
    if (value is Scope) return toJs(value.bindings)
    if (value is ObjectMap<*>) return value.obj
    if (value == null || jsTypeOf(value) != "object" && jsTypeOf(value) != "function") return value
    if (toJsCache.has(value) as Boolean) return toJsCache.get(value)
    val native: dynamic = when (value) {
        FunctionBind -> js("Function.prototype.bind")
        is ExpressionFunction -> {
            val invoke = { receiver: dynamic, args: Array<dynamic> ->
                toJs(value.callWithReceiver(fromJs(receiver), *args.map(::fromJs).toTypedArray()))
            }
            js("(function(call){return function(){return call(this, Array.from(arguments));};})(invoke)")
        }
        is List<*> -> value.map(::toJs).toTypedArray()
        is Map<*, *> -> {
            val obj = plainObject()
            for ((key, element) in value) obj[key] = toJs(element)
            obj
        }
        else -> return value
    }
    toJsCache.set(value, native)
    // Arrays and objects become live native values once exposed. Import them through
    // the native views on the next read so caller mutations remain visible.
    if (value is ExpressionFunction) fromJsCache.set(native, value)
    return native
}
