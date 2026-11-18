package org.expreval

import kotlin.test.*

class LanguageParityTest {
    private val parser = Parser()

    @Test
    fun arithmeticAndShortCircuiting() {
        assertEquals(-512.0, parser.evaluate("-2^3^2"))
        assertEquals(19.5, parser.evaluate("0x10 + 0b11 + .5"))
        assertEquals(true, parser.evaluate("true or missing"))
        assertEquals(Undefined, parser.evaluate("[][0]"))
        assertEquals(false, parser.evaluate("[] == []"))
        assertEquals(true, parser.evaluate("3 in [2,3]"))
        assertEquals(3.0, parser.evaluate("'1' + 2"))
        assertEquals("12", parser.parse("a + b").toFunction("a,b").call("1", 2))
    }

    @Test
    fun assignmentsDefinitionsAndCalls() {
        val scope = mutableMapOf<String, Any?>()
        assertEquals(120.0, parser.evaluate("f(n)=n>1 ? n*f(n-1) : 1; x=f(5)", scope))
        assertEquals(120.0, scope["x"])
        assertEquals(listOf(2.0, 3.0), parser.evaluate("p(x)=x>1;filter(p,[1,2,3])"))
    }

    @Test
    fun symbolsAndTransformations() {
        val expr = parser.parse("value.x + 2*y")
        assertEquals(listOf("value.x", "y"), expr.variables(SymbolOptions(true)))
        val simplified = expr.simplify(mapOf("value" to mapOf("x" to 4)))
        assertEquals("(4 + (2 * y))", simplified.toString())
        assertEquals(10.0, simplified.substitute("y", "x+1").evaluate(mapOf("x" to 2)))
    }
}
