package org.expreval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LanguageParityTest {
    private val parser = Parser()

    @Test
    fun literalsAndPrecedence() {
        assertEquals(24.5, parser.evaluate("0x10 + 0b10 + .5e1 + 1.5 /* comment */"))
        assertEquals(19.5, parser.evaluate("0x10 + 0b11 + .5"))
        assertEquals(-512.0, parser.evaluate("-2^3^2"))
        assertEquals(0.0625, parser.evaluate("2^-4"))
        assertEquals(120.0, parser.evaluate("5!"))
        assertEquals(6.0, parser.evaluate("2∙3"))
        assertEquals("a\nb", parser.evaluate("'a\\nb'"))
    }

    @Test
    fun shortCircuitAndStrictEquality() {
        assertEquals(true, parser.evaluate("true or missing"))
        assertEquals(false, parser.evaluate("false and missing"))
        assertEquals(3.0, parser.evaluate("false ? missing : 3"))
        assertEquals(false, parser.evaluate("'1' == 1"))
        assertEquals(5.0, parser.evaluate("'2' + 3"))
        assertFailsWith<IllegalArgumentException> { parser.evaluate("if(true, 3, missing)") }
    }

    @Test
    fun arraysStringsAndBuiltins() {
        assertEquals(listOf(1.0, 2.0, listOf(3.0)), parser.evaluate("[1] || [2,[3]]"))
        assertEquals("b", parser.evaluate("'abc'[1.9]"))
        assertEquals(true, parser.evaluate("2 in [1,2]"))
        assertEquals(5.0, parser.evaluate("hypot(3,4)"))
        assertEquals(1.01, parser.evaluate("roundTo(1.005,2)"))
        assertEquals("2-3", parser.evaluate("join('-', map(abs, [-2,-3]))"))
        assertEquals(6.0, parser.evaluate("f(a,x)=a+x; fold(f,0,[1,2,3])"))
        assertEquals(listOf(2.0, 3.0), parser.evaluate("f(x)=x>1; filter(f,[1,2,3])"))
    }

    @Test
    fun statementsAndFunctions() {
        val scope = mutableMapOf<String, Any?>()
        assertEquals(15.0, parser.evaluate("y=5; (f(x)=x*y)(3)", scope))
        assertEquals(5.0, scope["y"])
        assertEquals(120.0, parser.evaluate("(f(x)=x>1 ? x*f(x-1) : 1)(5)"))
        assertEquals(120.0, parser.evaluate("f(n)=n>1 ? n*f(n-1) : 1; x=f(5)", scope))
        assertEquals(120.0, scope["x"])
        assertEquals(3.0, parser.evaluate("1;2;3;"))
        assertEquals(17.0, parser.evaluate("2+(x=3;y=4;z=x*y)+3"))
    }

    @Test
    fun symbolsAndTransformations() {
        val expr = parser.parse("max(conf.min, conf.max) + x^2")
        assertEquals(listOf("max", "conf", "x"), expr.symbols())
        assertEquals(listOf("conf.min", "conf.max", "x"), expr.variables(SymbolOptions(true)))
        assertEquals("(max(conf.min, conf.max) + (x ^ 2))", expr.toString())
        val simplified = expr.simplify(mapOf("conf" to mapOf("min" to 1, "max" to 2)))
        assertEquals("(max(1, 2) + (x ^ 2))", simplified.toString())
        assertEquals(11.0, simplified.toFunction("x").call(3))
        assertEquals("(x = (((y + 1) * 2)))", parser.parse("x = x * 2").substitute("x", "y+1").toString())
        assertEquals(3, parser.parse("1+2").tokens.size)

        val nested = parser.parse("value.x + 2*y")
        assertEquals(listOf("value.x", "y"), nested.variables(SymbolOptions(true)))
        val replaced = nested.simplify(mapOf("value" to mapOf("x" to 4)))
        assertEquals("(4 + (2 * y))", replaced.toString())
        assertEquals(10.0, replaced.substitute("y", "x+1").evaluate(mapOf("x" to 2)))
    }

    @Test
    fun configurationAndCallableRegistration() {
        val configured = Parser(ParserOptions(false, mapOf("multiply" to false, "fndef" to false)))
        for (input in listOf("2*3", "x.y", "f(x)=x", "5/", "0b2", "'bad\\q'")) {
            assertFailsWith<IllegalArgumentException>(input) { configured.parse(input) }
        }
        val twice = ExpressionFunction { args -> (args[0] as Number).toDouble() * 2 }
        parser.functions["twice"] = twice
        assertEquals(8.0, parser.evaluate("twice(4)"))
        assertEquals(8.0, parser.evaluate("f(4)", mapOf("f" to twice)))
        assertFailsWith<SecurityException> {
            parser.evaluate("f(4)", mapOf("f" to ExpressionFunction { 0 }))
        }
        assertEquals(7, parser.evaluate("x.value", mapOf("x" to mapOf("value" to 7))))
    }

    @Test
    fun callableParity() {
        assertEquals("12", parser.parse("a + b").toFunction(listOf("a", "b")).call("1", 2))
        assertEquals(3.0, parser.parse("(x = 2; x); x+1").toFunction().call())
        assertEquals(4.0, parser.parse("(x = 2; x) + x").toFunction().call())
        assertEquals(listOf(2.0, 2.0), parser.parse("[(x=2; x), x]").toFunction().call())
        assertEquals(2.0, parser.parse("min((x=2; x), x)").toFunction().call())
        assertEquals(6.0, parser.parse("(f(x) = g(y) = x*y)(a)(b)").toFunction("a,b").call(2, 3))
        assertEquals(true, parser.parse("(x; false) or true").toFunction("x").call(2))
        assertEquals(Undefined, parser.parse("x").toFunction("x").call())
        assertEquals(7.0, Parser.parse("3+4").evaluate())
    }

    @Test
    fun numericCoercionAndIdentity() {
        assertEquals(false, parser.evaluate("[] == []"))
        assertEquals(false, parser.evaluate("[1] == [1]"))
        assertEquals(true, parser.evaluate("a == a", mapOf("a" to listOf(1))))
        assertEquals(true, parser.evaluate("(0/0) in [0/0]"))
        assertEquals(-1.0, parser.evaluate("indexOf(0/0, [0/0])"))
        assertEquals(3, parser.evaluate("x[4294967296]", mapOf("x" to arrayOf(3))))
        assertEquals(Undefined, parser.evaluate("[][0]"))
        assertEquals(0.0, parser.evaluate("sum([nil, false])", mapOf("nil" to null)))
        assertEquals(true, parser.evaluate("'ell' in 'hello'"))
        assertEquals(1.0, parser.evaluate("sum(['0b1'])"))
    }
}
