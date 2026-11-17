package org.expreval

import org.expreval.InstructionType.*

private val blockedName = Regex("^__proto__|prototype|constructor$")

internal fun guardName(name: String) {
    if (blockedName.containsMatchIn(name)) throw SecurityException("prototype access detected")
}

/** Use the caller's map for assignments when it supports mutation. */
internal class Scope(
    values: Map<String, Any?>,
) {
    private var current = values
    val values get() = current

    fun set(
        name: String,
        value: Any?,
    ) {
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

internal class Evaluator(
    private val expression: Expression,
    private val callable: Boolean = false,
) {
    private class Deferred(
        val items: List<Instruction>,
    )

    private class Defined(
        val parser: Parser,
        val function: ExpressionFunction,
    ) : ExpressionFunction by function

    private class FunctionReference(
        val receiver: ExpressionFunction,
        val function: ExpressionFunction,
    )

    private fun allowed(value: ExpressionFunction): Boolean =
        (callable && value === FunctionBind) ||
            (callable && value is BoundFunction && allowed(value.target)) ||
            (value is Defined && value.parser === expression.parser) ||
            sequenceOf(expression.functions, expression.unaryOps, expression.binaryOps, expression.ternaryOps)
                .any { map -> map.values.any { it === value } }

    private fun checkValue(
        value: Any?,
        message: String,
    ): Any? {
        if (value is ExpressionFunction && !allowed(value)) throw SecurityException(message)
        return value
    }

    fun run(
        items: List<Instruction>,
        scope: Scope,
        preserveReference: Boolean = false,
    ): Any? {
        val stack = mutableListOf<Any?>()

        fun pop(): Any? = if (stack.isEmpty()) Undefined else stack.removeAt(stack.lastIndex)

        fun resolveReference(value: Any?): Any? = if (value is Deferred) run(value.items, scope, true) else value

        fun resolve(value: Any?): Any? =
            when (val resolved = resolveReference(value)) {
                is FunctionReference -> resolved.function
                else -> resolved
            }

        fun leftValue(value: Any?) = if (callable) resolve(value) else value
        for (item in items) {
            val op = item.value.toString()
            val value =
                when (item.type) {
                    INUMBER, IVARNAME -> {
                        item.value
                    }

                    IVAR -> {
                        guardName(op)
                        expression.functions[op]
                            ?: expression.unaryOps[op]?.takeIf { expression.parser.isOperatorEnabled(op) }
                            ?: property(scope.values, op).let {
                                if (it === Undefined && !(callable && scope.values.containsKey(op))) {
                                    throw IllegalArgumentException("undefined variable: $op")
                                }
                                checkValue(it, "Variable references an unallowed function: $op")
                            }
                    }

                    IOP1 -> {
                        expression.unaryOps.getValue(op).call(resolve(pop()))
                    }

                    IOP2 -> {
                        val right = pop()
                        val left = pop()
                        when (op) {
                            "and" -> {
                                truth(leftValue(left)) && truth(resolve(right))
                            }

                            "or" -> {
                                truth(leftValue(left)) || truth(resolve(right))
                            }

                            "=" -> {
                                val name = jsString(left)
                                guardName(name)
                                val result = resolve(right)
                                val setter = expression.binaryOps.getValue(op)
                                setter.call(
                                    left,
                                    result,
                                    object : MutableMap<String, Any?> by scope.values.toMutableMap() {
                                        override fun put(
                                            key: String,
                                            value: Any?,
                                        ): Any? {
                                            val previous = scope.values[key]
                                            scope.set(key, value)
                                            return previous
                                        }
                                    },
                                )
                            }

                            else -> {
                                val a = resolve(left)
                                val b = resolve(right)
                                when {
                                    callable && op == "+" -> nativeAdd(a, b)
                                    callable && op == "in" -> hasProperty(a, b)
                                    else -> expression.binaryOps.getValue(op).call(a, b)
                                }
                            }
                        }
                    }

                    IOP3 -> {
                        val no = pop()
                        val yes = pop()
                        val condition = pop()
                        if (op == "?") {
                            resolve(if (truth(leftValue(condition))) yes else no)
                        } else {
                            expression.ternaryOps.getValue(op).call(resolve(condition), resolve(yes), resolve(no))
                        }
                    }

                    IEXPR -> {
                        Deferred(item.block())
                    }

                    IEXPREVAL -> {
                        item.value
                    }

                    IMEMBER -> {
                        guardName(op)
                        val receiver = leftValue(pop())
                        val member = property(receiver, op)
                        if (receiver is ExpressionFunction) {
                            // The reference evaluator exposes function properties without admitting calls.
                            if (callable && member === FunctionBind) FunctionReference(receiver, member) else member
                        } else {
                            checkValue(member, "Is not an allowed function in MEMBER.")
                        }
                    }

                    IARRAY -> {
                        val args = List(item.value as Int) { pop() }.asReversed()
                        if (callable) args.map(::resolve) else ArrayList(args)
                    }

                    IFUNCALL -> {
                        val args = arrayOfNulls<Any?>(item.value as Int)
                        for (i in args.indices.reversed()) {
                            val arg = pop()
                            args[i] = if (callable) arg else resolve(arg)
                        }
                        val callee = if (callable) resolveReference(pop()) else pop()
                        if (callable) for (i in args.indices) args[i] = resolve(args[i])
                        val reference = callee as? FunctionReference
                        val function = reference?.function ?: callee
                        val bindCall =
                            callable && function === FunctionBind && reference != null &&
                                allowed(reference.receiver)
                        if (function !is ExpressionFunction || (!bindCall && !allowed(function))) {
                            throw SecurityException("Is not an allowed function.")
                        }
                        // Higher-order builtins should only dispatch callable arguments admitted by this parser.
                        args.forEach { checkValue(it, "Is not an allowed function.") }
                        if (bindCall) function.callWithReceiver(reference.receiver, *args) else function.call(*args)
                    }

                    IFUNDEF -> {
                        val body = pop() as? Deferred ?: throw IllegalArgumentException("expected function body")
                        val names = List(item.value as Int) { jsString(pop()) }.asReversed()
                        val name = jsString(pop())
                        guardName(name)
                        val function =
                            Defined(
                                expression.parser,
                                fn { args ->
                                    val local = scope.values.toMutableMap()
                                    names.forEachIndexed { i, parameter -> local[parameter] = args.arg(i) }
                                    run(body.items, Scope(local))
                                },
                            )
                        scope.set(name, function)
                        function
                    }

                    IENDSTATEMENT -> {
                        leftValue(pop())
                        continue
                    }
                }
            stack.add(value)
        }
        require(stack.size <= 1) { "invalid Expression (parity)" }
        val first = pop()
        return when {
            // Parentheses preserve a method receiver; a statement sequence produces a detached value.
            preserveReference && items.none { it.type == IENDSTATEMENT } -> resolveReference(first)
            !callable && first is Number && first.toDouble() == 0.0 -> 0.0
            else -> resolve(first)
        }
    }
}
