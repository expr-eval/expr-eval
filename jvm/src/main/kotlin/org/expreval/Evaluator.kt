package org.expreval

import org.expreval.InstructionType.*

private val blockedName = Regex("^__proto__|prototype|constructor$")
internal fun guardName(name: String) {
    if (blockedName.containsMatchIn(name)) throw SecurityException("prototype access detected")
}

/** Use the caller's map for assignments when it supports mutation. */
internal class Scope(values: Map<String, Any?>) {
    private var current = values
    val values get() = current

    fun set(name: String, value: Any?) {
        try {
            @Suppress("UNCHECKED_CAST")
            (current as MutableMap<String, Any?>)[name] = value
        } catch (_: UnsupportedOperationException) {
            current = current.toMutableMap().apply { put(name, value) }
        } catch (_: ClassCastException) {
            current = current.toMutableMap().apply { put(name, value) }
        }
    }
}

internal class Evaluator(private val expression: Expression) {
    private class Deferred(val items: List<Instruction>)
    private class Defined(val parser: Parser, val function: ExpressionFunction) : ExpressionFunction by function

    private fun allowed(value: ExpressionFunction): Boolean =
        (value is Defined && value.parser === expression.parser) ||
            sequenceOf(expression.functions, expression.unaryOps, expression.binaryOps, expression.ternaryOps)
                .any { map -> map.values.any { it === value } }

    private fun checkValue(value: Any?, message: String): Any? {
        if (value is ExpressionFunction && !allowed(value)) throw SecurityException(message)
        return value
    }

    fun run(items: List<Instruction>, scope: Scope): Any? {
        val stack = mutableListOf<Any?>()
        fun pop(): Any? = if (stack.isEmpty()) Undefined else stack.removeAt(stack.lastIndex)
        fun resolve(value: Any?): Any? = if (value is Deferred) run(value.items, scope) else value
        for (item in items) {
            val op = item.value.toString()
            val value = when (item.type) {
                INUMBER, IVARNAME -> item.value
                IVAR -> {
                    guardName(op)
                    expression.functions[op]
                        ?: expression.unaryOps[op]?.takeIf { expression.parser.isOperatorEnabled(op) }
                        ?: property(scope.values, op).let {
                            if (it === Undefined) throw IllegalArgumentException("undefined variable: $op")
                            checkValue(it, "Variable references an unallowed function: $op")
                        }
                }
                IOP1 -> expression.unaryOps.getValue(op).call(resolve(pop()))
                IOP2 -> {
                    val right = pop()
                    val left = pop()
                    when (op) {
                        "and" -> truth(left) && truth(resolve(right))
                        "or" -> truth(left) || truth(resolve(right))
                        "=" -> {
                            val name = jsString(left)
                            guardName(name)
                            val result = resolve(right)
                            val setter = expression.binaryOps.getValue(op)
                            setter.call(left, result, object : MutableMap<String, Any?> by scope.values.toMutableMap() {
                                override fun put(key: String, value: Any?): Any? {
                                    val previous = scope.values[key]
                                    scope.set(key, value)
                                    return previous
                                }
                            })
                        }
                        else -> expression.binaryOps.getValue(op).call(resolve(left), resolve(right))
                    }
                }
                IOP3 -> {
                    val no = pop()
                    val yes = pop()
                    val condition = pop()
                    if (op == "?") resolve(if (truth(condition)) yes else no)
                    else expression.ternaryOps.getValue(op).call(resolve(condition), resolve(yes), resolve(no))
                }
                IEXPR -> Deferred(item.block())
                IEXPREVAL -> item.value
                IMEMBER -> {
                    guardName(op)
                    checkValue(property(pop(), op), "Is not an allowed function in MEMBER.")
                }
                IARRAY -> List(item.value as Int) { pop() }.asReversed().toList()
                IFUNCALL -> {
                    val args = arrayOfNulls<Any?>(item.value as Int)
                    for (i in args.indices.reversed()) args[i] = resolve(pop())
                    val function = pop()
                    if (function !is ExpressionFunction || !allowed(function))
                        throw SecurityException("Is not an allowed function.")
                    // Higher-order builtins should only dispatch callable arguments admitted by this parser.
                    args.forEach { checkValue(it, "Is not an allowed function.") }
                    function.call(*args)
                }
                IFUNDEF -> {
                    val body = pop() as? Deferred ?: throw IllegalArgumentException("expected function body")
                    val names = List(item.value as Int) { jsString(pop()) }.asReversed()
                    val name = jsString(pop())
                    guardName(name)
                    val function = Defined(expression.parser, fn { args ->
                        val local = scope.values.toMutableMap()
                        names.forEachIndexed { i, parameter -> local[parameter] = args.arg(i) }
                        run(body.items, Scope(local))
                    })
                    scope.set(name, function)
                    function
                }
                IENDSTATEMENT -> {
                    pop()
                    continue
                }
            }
            stack.add(value)
        }
        require(stack.size <= 1) { "invalid Expression (parity)" }
        val first = pop()
        return if (first is Number && first.toDouble() == 0.0) 0.0 else resolve(first)
    }
}
