@file:OptIn(ExperimentalJsExport::class)

package org.expreval

// These adapters preserve the npm object's mutable fields and native values. Every expression
// operation goes through the shared parser and instruction evaluator.
@JsExport
@JsName("Instruction")
class JsInstruction(var type: String, value: dynamic = 0) {
    var value: dynamic = value ?: 0
    // The npm Instruction API returns the original literal (including a number or boolean).
    override fun toString(): dynamic = when (type) {
        "INUMBER", "IOP1", "IOP2", "IOP3", "IVAR", "IVARNAME", "IENDSTATEMENT" -> value
        "IFUNCALL", "IFUNDEF", "IARRAY", "IMEMBER" -> instruction().toString()
        else -> "Invalid Instruction"
    }
    internal fun instruction(): Instruction = readInstruction(this)
}

private fun readInstruction(item: dynamic): Instruction {
    val type = InstructionType.valueOf(item.type as String)
    val value: Any? = if (type == InstructionType.IEXPR) readTokens(item.value) else fromJs(item.value)
    return Instruction(type, value)
}

private fun readTokens(tokens: dynamic): List<Instruction> =
    (tokens as Array<dynamic>).map(::readInstruction)

private fun writeTokens(tokens: List<Instruction>): Array<JsInstruction> = tokens.map { item ->
    val value = if (item.type == InstructionType.IEXPR) writeTokens(item.block()) else toJs(item.value)
    JsInstruction(item.type.name, value)
}.toTypedArray()

private val engines: dynamic = js("new WeakMap()")
private object DefaultRegistries {
    private val defaults = Parser()
    val unary = toJs(defaults.unaryOps)
    val binary = toJs(defaults.binaryOps)
    val ternary = toJs(defaults.ternaryOps)
    val functions = toJs(defaults.functions)
    val consts = toJs(defaults.consts)

    init {
        // The Kotlin vararg bridge forwards every argument. Keep Function.length observable
        // to npm callers as it was on the original functions.
        for (name in keys(unary)) setArity(unary[name], 1)
        for (name in keys(binary)) setArity(binary[name], if (name == "=") 3 else 2)
        for (name in keys(functions)) {
            val arity = when (name) {
                "if", "fold" -> 3
                "roundTo", "map", "filter", "indexOf", "join" -> 2
                else -> 1
            }
            setArity(functions[name], arity)
        }

        // The npm API exposes native Math function identities through these registries.
        val math: dynamic = js("Math")
        val aliases = mapOf("ln" to "log", "lg" to "log10")
        for (name in keys(unary)) {
            val native = math[aliases[name] ?: name]
            if (jsTypeOf(native) == "function") unary[name] = native
        }
        unary["+"] = js("Number")
        binary["^"] = math.pow
        for (name in arrayOf("pow", "hypot", "atan2")) functions[name] = math[name]
        functions.pyt = math.hypot
    }

    private fun setArity(fn: dynamic, arity: Int) {
        js("Object.defineProperty(fn, 'length', { value: arity, configurable: true })")
    }
}

private fun functionMap(obj: dynamic) =
    ObjectMap(obj, { fromJs(it) as ExpressionFunction }, ::toJs)

private fun copyRegistry(defaults: dynamic): dynamic = js("Object.assign({}, defaults)")

@JsExport
@JsName("Parser")
class JsParser(var options: dynamic = plainObject()) {
    var unaryOps: dynamic = copyRegistry(DefaultRegistries.unary)
    var binaryOps: dynamic = copyRegistry(DefaultRegistries.binary)
    var ternaryOps: dynamic = copyRegistry(DefaultRegistries.ternary)
    var functions: dynamic = copyRegistry(DefaultRegistries.functions)
    var consts: dynamic = copyRegistry(DefaultRegistries.consts)

    init {
        engines.set(this, Parser())
    }

    internal fun core(): Parser {
        val engine = engines.get(this) as Parser
        val settings = defaultObject(options)
        engine.options = ParserOptions(settings.allowMemberAccess !== false,
            ObjectMap(defaultObject(settings.operators), { js("Boolean(it)") as Boolean }, { it }))
        engine.unaryOps = functionMap(unaryOps)
        engine.binaryOps = functionMap(binaryOps)
        engine.ternaryOps = functionMap(ternaryOps)
        engine.functions = functionMap(functions)
        engine.consts = scopeMap(consts)
        return engine
    }

    fun parse(expression: String): JsExpression = JsExpression(writeTokens(core().parse(expression).tokens), this)
    fun evaluate(expression: String, variables: dynamic = null): dynamic = parse(expression).evaluate(variables)
    fun isOperatorEnabled(op: String): Boolean = core().isOperatorEnabled(op)
}

@JsExport
@JsName("Expression")
class JsExpression(var tokens: dynamic, var parser: JsParser) {
    val unaryOps: dynamic get() = parser.unaryOps
    val binaryOps: dynamic get() = parser.binaryOps
    val ternaryOps: dynamic get() = parser.ternaryOps
    val functions: dynamic get() = parser.functions
    private fun core() = Expression(readTokens(tokens), parser.core())
    private fun wrap(expr: Expression) = JsExpression(writeTokens(expr.tokens), parser)
    fun simplify(values: dynamic = null): JsExpression = wrap(core().simplify(scopeMap(values)))
    fun substitute(variable: String, expr: dynamic): JsExpression =
        wrap(if (expr is JsExpression) core().substitute(variable, (expr as JsExpression).core())
             else core().substitute(variable, js("String(expr)") as String))
    fun evaluate(values: dynamic = null): dynamic = toJs(core().evaluate(scopeMap(values)))
    override fun toString(): String = core().toString()
    fun symbols(options: dynamic = null): Array<String> = core().symbols(SymbolOptions(options?.withMembers == true)).toTypedArray()
    fun variables(options: dynamic = null): Array<String> = core().variables(SymbolOptions(options?.withMembers == true)).toTypedArray()
    fun toJSFunction(param: dynamic = "", variables: dynamic = null): dynamic {
        val expression = core().simplify(scopeMap(variables))
        // Public Instruction objects can contain any JavaScript identifier.
        val symbols = expression.symbols()
        var addName = "exprEval\$add"
        while (addName in symbols) addName += '$'
        val code = render(expression.tokens, javascriptAdd = addName)
        // Preserve the npm API's native Function calling convention and live registry lookup.
        val body = """
            with(this.functions) with(this.ternaryOps) with(this.binaryOps) with(this.unaryOps) {
                const $addName = (left, right) => {
                    const add = this.binaryOps['+'];
                    return add(left, right);
                };
                return $code;
            }
        """.trimIndent()
        val compiled = js("new Function(param, body)")
        val receiver = this
        return js("(...args) => compiled.apply(receiver, args)")
    }
}
