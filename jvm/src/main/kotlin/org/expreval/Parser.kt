/*
 * Based on ndef.parser by Raphael Graf (http://www.undefined.ch/mparser/index.html).
 * Ported to JavaScript and modified by Matthew Crumley (http://silentmatt.com/).
 */
package org.expreval

/** A function available to expressions, with a Java-compatible vararg signature. */
fun interface ExpressionFunction {
    fun call(vararg args: Any?): Any?
}

data class ParserOptions @JvmOverloads constructor(
    val allowMemberAccess: Boolean = true,
    val operators: Map<String, Boolean> = emptyMap(),
)

data class SymbolOptions @JvmOverloads constructor(val withMembers: Boolean = false)

enum class InstructionType {
    INUMBER, IOP1, IOP2, IOP3, IVAR, IVARNAME, IFUNCALL, IFUNDEF, IEXPR, IEXPREVAL,
    IMEMBER, IENDSTATEMENT, IARRAY,
}

data class Instruction @JvmOverloads constructor(val type: InstructionType, val value: Any? = 0.0) {
    override fun toString(): String = when (type) {
        InstructionType.IFUNCALL -> "CALL ${jsString(value)}"
        InstructionType.IFUNDEF -> "DEF ${jsString(value)}"
        InstructionType.IARRAY -> "ARRAY ${jsString(value)}"
        InstructionType.IMEMBER -> ".$value"
        InstructionType.IEXPR, InstructionType.IEXPREVAL -> "Invalid Instruction"
        else -> jsString(value)
    }
}

/** Parses expressions and owns the registries used to evaluate them. */
class Parser @JvmOverloads constructor(val options: ParserOptions = ParserOptions()) {
    val unaryOps: MutableMap<String, ExpressionFunction> = unaryBuiltins()
    val binaryOps: MutableMap<String, ExpressionFunction> = binaryBuiltins()
    val ternaryOps: MutableMap<String, ExpressionFunction> = linkedMapOf(
        "?" to fn { if (truth(it.arg(0))) it.arg(1) else it.arg(2) },
    )
    val functions: MutableMap<String, ExpressionFunction> = functionBuiltins()
    val consts: MutableMap<String, Any?> = linkedMapOf(
        "E" to Math.E, "PI" to Math.PI, "true" to true, "false" to false,
    )

    fun parse(expression: String): Expression = Expression(Grammar(this, expression).parse(), this)

    @JvmOverloads
    fun evaluate(expression: String, variables: Map<String, Any?> = emptyMap()): Any? =
        parse(expression).evaluate(variables)

    fun isOperatorEnabled(op: String): Boolean = options.operators[optionNames[op] ?: op] != false

    companion object {
        private val defaultParser = Parser()
        fun parse(expression: String): Expression = defaultParser.parse(expression)
        fun evaluate(expression: String, variables: Map<String, Any?> = emptyMap()): Any? =
            defaultParser.evaluate(expression, variables)

        private val optionNames = mapOf(
            "+" to "add", "-" to "subtract", "*" to "multiply", "/" to "divide",
            "%" to "remainder", "^" to "power", "!" to "factorial", "||" to "concatenate",
            "<" to "comparison", ">" to "comparison", "<=" to "comparison", ">=" to "comparison",
            "==" to "comparison", "!=" to "comparison", "not" to "logical", "and" to "logical",
            "or" to "logical", "?" to "conditional", ":" to "conditional", "=" to "assignment",
            "[" to "array", "()=" to "fndef",
        )
    }
}
