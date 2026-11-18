package org.expreval

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BindTest {
    @Test
    fun `shared JavaScript reference cases`() {
        val cases = requireNotNull(javaClass.getResourceAsStream("/bind.tsv"))
            .bufferedReader().use { it.readLines() }
        for (line in cases) {
            val (mode, input, type, expected) = line.split('\t')
            val expression = Parser().parse(input)
            val run = { if (mode == "evaluate") expression.evaluate() else expression.toFunction().call() }
            if (type == "error") {
                assertEquals(expected, assertThrows(RuntimeException::class.java) { run() }.message, line)
            } else {
                val actual = run()
                when (type) {
                    "function" -> assertInstanceOf(ExpressionFunction::class.java, actual, line)
                    "number" -> assertEquals(expected.toDouble(), actual, line)
                    "boolean" -> assertEquals(expected.toBooleanStrict(), actual, line)
                    "array" -> assertEquals(expected.split(',').map(String::toDouble), actual, line)
                    else -> assertEquals(expected, actual, line)
                }
            }
        }
    }

    @Test
    fun `bind is a shared function property rather than undefined`() {
        val parser = Parser()
        val bind = parser.evaluate("min.bind")
        assertInstanceOf(ExpressionFunction::class.java, bind)
        assertSame(bind, parser.evaluate("max.bind"))
        assertSame(bind, parser.evaluate("min.bind.bind"))
        assertEquals(true, parser.evaluate("min.bind == max.bind"))
        assertTrue((parser.evaluate("32 + min.bind") as Double).isNaN())
        assertSame(bind, parser.parse("min.bind").toFunction().call())
        assertEquals("32function bind() { [native code] }", parser.parse("32 + min.bind").toFunction().call())
    }

    @Test
    fun `interpreted calls retain the function allowlist`() {
        val parser = Parser()
        for (input in listOf("min.bind()", "min.bind(0, 5)(3)", "b=min.bind; b()")) {
            assertThrows(SecurityException::class.java) { parser.evaluate(input) }
        }
        val bind = parser.evaluate("min.bind") as ExpressionFunction
        assertThrows(IllegalArgumentException::class.java) { bind.call() }
        assertThrows(SecurityException::class.java) { parser.evaluate("b", mapOf("b" to bind)) }
        parser.functions["b"] = bind
        // Interpreted calls detach methods; registering bind does not give it a function receiver.
        assertThrows(IllegalArgumentException::class.java) { parser.evaluate("min.bind()") }
    }

    @Test
    fun `callables bind and prepend arguments`() {
        val parser = Parser()
        val cases = mapOf(
            "min.bind()(3, 8)" to 3.0,
            "min.bind(0, 5)(3)" to 3.0,
            "(min.bind)(0, 5)(9)" to 5.0,
            "(min.bind(0, 5)).bind(1, 3)(9)" to 3.0,
            "pow.bind(0, 2)(3)" to 8.0,
            "(f(x,y)=x-y).bind(0, 8)(3)" to 5.0,
            "b=pow.bind(0,2); b(4)" to 16.0,
        )
        for ((input, expected) in cases) {
            assertEquals(expected, parser.parse(input).toFunction().call(), input)
        }
        assertEquals(listOf(1.0, 2.0), parser.parse("map(pow.bind(0, 2), [0,1])").toFunction().call())
    }

    @Test
    fun `bound receiver survives rebinding and bound arguments are copied`() {
        val bind = Parser().evaluate("min.bind") as ExpressionFunction
        val contextual = object : ExpressionFunction {
            override fun call(vararg args: Any?): Any? = error("receiver was lost")
            override fun callWithReceiver(receiver: Any?, vararg args: Any?): Any? =
                listOf(receiver) + args.toList()
        }
        val arguments = arrayOf<Any?>("first", 1)
        val bound = bind.callWithReceiver(contextual, *arguments) as ExpressionFunction
        arguments[1] = 99
        val rebound = bind.callWithReceiver(bound, "second", 2) as ExpressionFunction
        assertEquals(listOf("first", 1, 2, 3), rebound.call(3))
        assertNotSame(bound, bind.callWithReceiver(contextual, "first", 1))
        assertThrows(IllegalArgumentException::class.java) { bind.callWithReceiver(42) }
    }

    @Test
    fun `member controls and unsafe host functions remain blocked`() {
        val disabled = Parser(ParserOptions(allowMemberAccess = false))
        for (input in listOf("min.bind", "min.bind()", "32 + min.bind")) {
            assertThrows(IllegalArgumentException::class.java) { disabled.parse(input) }
        }
        val parser = Parser()
        val unsafe = ExpressionFunction { 42 }
        assertThrows(SecurityException::class.java) {
            parser.parse("f.bind(0)()").toFunction("f").call(unsafe)
        }
        assertThrows(SecurityException::class.java) {
            parser.evaluate("obj.bind", mapOf("obj" to mapOf("bind" to unsafe)))
        }
        assertThrows(SecurityException::class.java) { parser.evaluate("min.bind.constructor") }
        assertThrows(SecurityException::class.java) { parser.parse("min.bind.constructor").toFunction().call() }
    }
}
