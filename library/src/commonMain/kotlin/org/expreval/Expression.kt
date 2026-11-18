package org.expreval

import kotlin.jvm.JvmOverloads
import org.expreval.InstructionType.*

/** A parsed expression. It can be reused with different variable maps. */
class Expression(
    val tokens: List<Instruction>,
    val parser: Parser,
) {
    val unaryOps get() = parser.unaryOps
    val binaryOps get() = parser.binaryOps
    val ternaryOps get() = parser.ternaryOps
    val functions get() = parser.functions

    @JvmOverloads
    fun evaluate(values: Map<String, Any?> = linkedMapOf()): Any? = Evaluator(this).run(tokens, Scope(values))

    @JvmOverloads
    fun simplify(values: Map<String, Any?> = emptyMap()): Expression = Expression(simplify(tokens, values), parser)

    fun substitute(
        variable: String,
        replacement: Expression,
    ): Expression {
        fun replace(items: List<Instruction>): List<Instruction> =
            buildList {
                for (item in items) {
                    when {
                        item.type == IVAR && item.value == variable -> addAll(replacement.tokens)
                        item.type == IEXPR -> add(item.copy(value = replace(item.block())))
                        else -> add(item)
                    }
                }
            }
        return Expression(replace(tokens), parser)
    }

    fun substitute(
        variable: String,
        replacement: String,
    ): Expression = substitute(variable, parser.parse(replacement))

    fun substitute(
        variable: String,
        replacement: Number,
    ): Expression = substitute(variable, jsString(replacement))

    @JvmOverloads
    fun symbols(options: SymbolOptions = SymbolOptions()): List<String> {
        val symbols = linkedSetOf<String>()

        fun visit(items: List<Instruction>) {
            var previous: String? = null
            for (item in items) {
                when {
                    item.type == IVAR || item.type == IVARNAME -> {
                        val name = jsString(item.value)
                        if (options.withMembers) {
                            previous?.let(symbols::add)
                            previous = name
                        } else {
                            symbols.add(name)
                        }
                    }

                    item.type == IMEMBER && options.withMembers && previous != null -> {
                        previous += ".${item.value}"
                    }

                    item.type == IEXPR -> {
                        visit(item.block())
                    }

                    else -> {
                        previous?.let(symbols::add)
                        previous = null
                    }
                }
            }
            previous?.let(symbols::add)
        }
        visit(tokens)
        return symbols.toList()
    }

    @JvmOverloads
    fun variables(options: SymbolOptions = SymbolOptions()): List<String> = symbols(options).filterNot(functions::containsKey)

    /**
     * JVM counterpart of toJSFunction: bind positional parameters and return a reusable callable.
     * The callable uses simplified instructions and the parser's normal function policy.
     */
    @JvmOverloads
    fun toFunction(
        parameters: String = "",
        values: Map<String, Any?> = emptyMap(),
    ): ExpressionFunction = toFunction(if (parameters.isBlank()) emptyList() else parameters.split(',').map(String::trim), values)

    @JvmOverloads
    fun toFunction(
        parameters: List<String>,
        values: Map<String, Any?> = emptyMap(),
    ): ExpressionFunction {
        val names = parameters.toList()
        require(names.all { parameter.matches(it) }) { "Invalid parameter list: $parameters" }
        val simplified = simplify(values)
        val program = callableProgram(simplified.tokens)
        return fn { args ->
            val scope = linkedMapOf<String, Any?>()
            names.forEachIndexed { index, name -> scope[name] = args.arg(index) }
            Evaluator(simplified, callable = true).run(program, Scope(scope))
        }
    }

    override fun toString(): String = render(tokens)

    private fun simplify(
        items: List<Instruction>,
        values: Map<String, Any?>,
    ): List<Instruction> {
        val pending = mutableListOf<Instruction>()
        val result = mutableListOf<Instruction>()

        fun pop() = pending.removeAt(pending.lastIndex).value

        fun literal(value: Any?) = pending.add(Instruction(INUMBER, value))

        fun flush() {
            result.addAll(pending)
            pending.clear()
        }
        for (item in items) {
            val op = item.value as? String ?: ""
            when {
                item.type == INUMBER || item.type == IVARNAME -> {
                    pending.add(item)
                }

                item.type == IVAR && values.containsKey(op) -> {
                    literal(values[op])
                }

                item.type == IOP1 && pending.isNotEmpty() -> {
                    literal(unaryOps.getValue(op).call(pop()))
                }

                item.type == IOP2 && pending.size >= 2 -> {
                    val right = pop()
                    literal(binaryOps.getValue(op).call(pop(), right))
                }

                item.type == IOP3 && pending.size >= 3 -> {
                    val no = pop()
                    val yes = pop()
                    literal(ternaryOps.getValue(op).call(pop(), yes, no))
                }

                item.type == IMEMBER && pending.isNotEmpty() -> {
                    guardName(op)
                    literal(property(pop(), op))
                }

                item.type == IEXPR -> {
                    flush()
                    result.add(item.copy(value = simplify(item.block(), values)))
                }

                else -> {
                    flush()
                    result.add(item)
                }
            }
        }
        flush()
        return result
    }

    companion object {
        private val parameter = Regex("[\\p{L}_$][\\p{L}\\p{N}_$]*")
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Instruction.block() = value as List<Instruction>

internal fun render(items: List<Instruction>, javascript: Boolean = false): String {
    val stack = mutableListOf<String>()

    fun pop() = if (stack.isEmpty()) "undefined" else stack.removeAt(stack.lastIndex)

    fun args(count: Any?) = List(count as Int) { pop() }.asReversed().joinToString(", ")
    for (item in items) {
        val op = item.value as? String ?: ""
        val part =
            when (item.type) {
                INUMBER -> {
                    literalText(item.value)
                }

                IVAR, IVARNAME -> {
                    op
                }

                IOP1 -> {
                    val operand = pop()
                    when (op) {
                        "-", "+" -> "($op$operand)"
                        "!" -> if (javascript) "fac($operand)" else "($operand!)"
                        "not" -> if (javascript) "(!$operand)" else "(not $operand)"
                        else -> if (javascript) "$op($operand)" else "($op $operand)"
                    }
                }

                IOP2 -> {
                    val right = pop()
                    val left = pop()
                    if (javascript) {
                        when (op) {
                            "^" -> "Math.pow($left, $right)"
                            "and" -> "(!!$left && !!$right)"
                            "or" -> "(!!$left || !!$right)"
                            "||" -> "((a,b) => Array.isArray(a) && Array.isArray(b) ? a.concat(b) : String(a) + String(b))($left,$right)"
                            "==" -> "($left === $right)"
                            "!=" -> "($left !== $right)"
                            "[" -> "$left[($right) | 0]"
                            else -> "($left $op $right)"
                        }
                    } else if (op == "[") "$left[$right]" else "($left $op $right)"
                }

                IOP3 -> {
                    require(op == "?") { "invalid Expression" }
                    val no = pop()
                    val yes = pop()
                    "(${pop()} ? $yes : $no)"
                }

                IFUNCALL -> {
                    val args = args(item.value)
                    "${pop()}($args)"
                }

                IFUNDEF -> {
                    val body = pop()
                    val args = args(item.value)
                    val name = pop()
                    if (javascript) "($name = function($args) { return $body })" else "($name($args) = $body)"
                }

                IMEMBER -> {
                    "${pop()}.$op"
                }

                IARRAY -> {
                    "[${args(item.value)}]"
                }

                IEXPR -> {
                    "(${render(item.block(), javascript)})"
                }

                IENDSTATEMENT -> {
                    continue
                }

                IEXPREVAL -> {
                    throw IllegalArgumentException("invalid Expression")
                }
            }
        stack.add(part)
    }
    return if (stack.isEmpty()) "undefined" else stack.joinToString(if (javascript) "," else ";")
}

private fun literalText(
    value: Any?,
    wrapNegative: Boolean = true,
): String {
    if (value is Number && value.toDouble() < 0 && wrapNegative) return "(${jsString(value)})"
    if (value is String) {
        return buildString {
            append('"')
            value.forEachIndexed { i, c ->
                append(
                    when (c) {
                        '"' -> {
                            "\\\""
                        }

                        '\\' -> {
                            "\\\\"
                        }

                        '\b' -> {
                            "\\b"
                        }

                        '\t' -> {
                            "\\t"
                        }

                        '\n' -> {
                            "\\n"
                        }

                        '\r' -> {
                            "\\r"
                        }

                        '\u000c' -> {
                            "\\f"
                        }

                        else -> {
                            if (c < ' ' || c == '\u2028' || c == '\u2029' ||
                                (c.isHighSurrogate() && value.getOrNull(i + 1)?.isLowSurrogate() != true) ||
                                (c.isLowSurrogate() && value.getOrNull(i - 1)?.isHighSurrogate() != true)
                            ) {
                                "\\u" + c.code.toString(16).padStart(4, '0')
                            } else {
                                c
                            }
                        }
                    },
                )
            }
            append('"')
        }
    }
    return array(value)?.joinToString(", ", "[", "]") { literalText(it, false) } ?: jsString(value)
}
