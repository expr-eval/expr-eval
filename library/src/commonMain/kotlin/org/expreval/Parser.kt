/*!
 Based on ndef.parser, by Raphael Graf(r@undefined.ch)
 http://www.undefined.ch/mparser/index.html

 Ported to JavaScript and modified by Matthew Crumley (email@matthewcrumley.com, http://silentmatt.com/)

 You are free to use and modify this code in anyway you find useful. Please leave this comment in the code
 to acknowledge its original source. If you feel like it, I enjoy hearing about projects that use my code,
 but don't feel like you have to let me know or ask permission.
*/
package org.expreval

import kotlin.jvm.JvmOverloads

/** A function available to expressions, with a Java-compatible vararg signature. */
fun interface ExpressionFunction {
    fun call(vararg args: Any?): Any?

    /** Invokes this function with a JavaScript-style receiver; ordinary lambdas ignore it. */
    fun callWithReceiver(receiver: Any?, vararg args: Any?): Any? = call(*args)
}

data class ParserOptions
    @JvmOverloads
    constructor(
        val allowMemberAccess: Boolean = true,
        val operators: Map<String, Boolean> = emptyMap(),
    )

data class SymbolOptions
    @JvmOverloads
    constructor(
        val withMembers: Boolean = false,
    )

enum class InstructionType {
    INUMBER,
    IOP1,
    IOP2,
    IOP3,
    IVAR,
    IVARNAME,
    IFUNCALL,
    IFUNDEF,
    IEXPR,
    IEXPREVAL,
    IMEMBER,
    IENDSTATEMENT,
    IARRAY,
}

data class Instruction
    @JvmOverloads
    constructor(
        val type: InstructionType,
        val value: Any? = 0.0,
    ) {
        override fun toString(): String =
            when (type) {
                InstructionType.IFUNCALL -> "CALL ${jsString(value)}"
                InstructionType.IFUNDEF -> "DEF ${jsString(value)}"
                InstructionType.IARRAY -> "ARRAY ${jsString(value)}"
                InstructionType.IMEMBER -> ".$value"
                InstructionType.IEXPR, InstructionType.IEXPREVAL -> "Invalid Instruction"
                else -> jsString(value)
            }
    }

/** Parses expressions and owns the registries used to evaluate them. */
class Parser
    @JvmOverloads
    constructor(
        options: ParserOptions = ParserOptions(),
    ) {
        internal var onDefine: ((ExpressionFunction) -> Unit)? = null

        var options: ParserOptions = options
            internal set
        var unaryOps: MutableMap<String, ExpressionFunction> = unaryBuiltins()
            internal set
        var binaryOps: MutableMap<String, ExpressionFunction> = binaryBuiltins()
            internal set
        var ternaryOps: MutableMap<String, ExpressionFunction> =
            linkedMapOf(
                "?" to conditionalFunction,
            )
            internal set
        var functions: MutableMap<String, ExpressionFunction> = functionBuiltins()
            internal set
        var consts: MutableMap<String, Any?> =
            linkedMapOf(
                "E" to kotlin.math.E,
                "PI" to kotlin.math.PI,
                "true" to true,
                "false" to false,
            )
            internal set

        fun parse(expression: String): Expression = Expression(Grammar(this, expression).parse(), this)

        @JvmOverloads
        fun evaluate(
            expression: String,
            variables: Map<String, Any?> = emptyMap(),
        ): Any? = parse(expression).evaluate(variables)

        fun isOperatorEnabled(op: String): Boolean = options.operators[optionNames[op] ?: op] != false

        companion object {
            private val defaultParser = Parser()

            fun parse(expression: String): Expression = defaultParser.parse(expression)

            @JvmOverloads
            fun evaluate(
                expression: String,
                variables: Map<String, Any?> = emptyMap(),
            ): Any? = defaultParser.evaluate(expression, variables)

            private val optionNames =
                mapOf(
                    "+" to "add",
                    "-" to "subtract",
                    "*" to "multiply",
                    "/" to "divide",
                    "%" to "remainder",
                    "^" to "power",
                    "!" to "factorial",
                    "||" to "concatenate",
                    "<" to "comparison",
                    ">" to "comparison",
                    "<=" to "comparison",
                    ">=" to "comparison",
                    "==" to "comparison",
                    "!=" to "comparison",
                    "not" to "logical",
                    "and" to "logical",
                    "or" to "logical",
                    "?" to "conditional",
                    ":" to "conditional",
                    "=" to "assignment",
                    "[" to "array",
                    "()=" to "fndef",
                )
        }
    }
