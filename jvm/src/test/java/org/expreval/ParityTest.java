package org.expreval;

import java.util.Objects;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ParityTest {
    public static void main(String[] args) {
        ParityTest test = new ParityTest();
        test.literalsAndPrecedence();
        test.shortCircuitAndStrictEquality();
        test.arraysStringsAndBuiltins();
        test.statementsAndFunctions();
        test.symbolicOperationsAndCallable();
        test.configurationAndCallableRegistration();
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertThrows(Class<? extends Exception> type, Runnable action, String... context) {
        try {
            action.run();
        } catch (Exception e) {
            if (type.isInstance(e)) return;
            throw new AssertionError("Unexpected exception for " + String.join(", ", context), e);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + " for " + String.join(", ", context));
    }

    private final Parser parser = new Parser();

    void literalsAndPrecedence() {
        assertEquals(24.5, parser.evaluate("0x10 + 0b10 + .5e1 + 1.5 /* comment */"));
        assertEquals(-512.0, parser.evaluate("-2^3^2"));
        assertEquals(0.0625, parser.evaluate("2^-4"));
        assertEquals(120.0, parser.evaluate("5!"));
        assertEquals(6.0, parser.evaluate("2∙3"));
        assertEquals("a\nb", parser.evaluate("'a\\nb'"));
    }

    void shortCircuitAndStrictEquality() {
        assertEquals(true, parser.evaluate("true or missing"));
        assertEquals(false, parser.evaluate("false and missing"));
        assertEquals(3.0, parser.evaluate("false ? missing : 3"));
        assertEquals(false, parser.evaluate("'1' == 1"));
        assertEquals(5.0, parser.evaluate("'2' + 3"));
        assertThrows(IllegalArgumentException.class, () -> parser.evaluate("if(true, 3, missing)"));
    }

    void arraysStringsAndBuiltins() {
        assertEquals(List.of(1.0, 2.0, List.of(3.0)), parser.evaluate("[1] || [2,[3]]"));
        assertEquals("b", parser.evaluate("'abc'[1.9]"));
        assertEquals(true, parser.evaluate("2 in [1,2]"));
        assertEquals(5.0, parser.evaluate("hypot(3,4)"));
        assertEquals(1.01, parser.evaluate("roundTo(1.005,2)"));
        assertEquals("2-3", parser.evaluate("join('-', map(abs, [-2,-3]))"));
        assertEquals(6.0, parser.evaluate("f(a,x)=a+x; fold(f,0,[1,2,3])"));
        assertEquals(List.of(2.0, 3.0), parser.evaluate("f(x)=x>1; filter(f,[1,2,3])"));
    }

    void statementsAndFunctions() {
        Map<String, Object> values = new HashMap<>();
        assertEquals(15.0, parser.evaluate("y=5; (f(x)=x*y)(3)", values));
        assertEquals(5.0, values.get("y"));
        assertEquals(120.0, parser.evaluate("(f(x)=x>1 ? x*f(x-1) : 1)(5)"));
        assertEquals(3.0, parser.evaluate("1;2;3;"));
        assertEquals(17.0, parser.evaluate("2+(x=3;y=4;z=x*y)+3"));
    }

    void symbolicOperationsAndCallable() {
        Expression expr = parser.parse("max(conf.min, conf.max) + x^2");
        assertEquals(List.of("max", "conf", "x"), expr.symbols());
        assertEquals(List.of("conf.min", "conf.max", "x"), expr.variables(new SymbolOptions(true)));
        assertEquals("(max(conf.min, conf.max) + (x ^ 2))", expr.toString());
        Expression simplified = expr.simplify(Map.of("conf", Map.of("min", 1, "max", 2)));
        assertEquals("(max(1, 2) + (x ^ 2))", simplified.toString());
        assertEquals(11.0, simplified.toFunction("x").call(3));
        assertEquals("(x = (((y + 1) * 2)))", parser.parse("x = x * 2").substitute("x", "y+1").toString());
        assertEquals(3, parser.parse("1+2").getTokens().size());
    }

    void configurationAndCallableRegistration() {
        Parser configured = new Parser(new ParserOptions(false, Map.of("multiply", false, "fndef", false)));
        for (String input : List.of("2*3", "x.y", "f(x)=x", "5/", "0b2", "'bad\\q'")) {
            assertThrows(IllegalArgumentException.class, () -> configured.parse(input), input);
        }
        ExpressionFunction twice = args -> ((Number) args[0]).doubleValue() * 2;
        parser.getFunctions().put("twice", twice);
        assertEquals(8.0, parser.evaluate("twice(4)"));
        assertEquals(8.0, parser.evaluate("f(4)", Map.of("f", twice)));
        assertThrows(SecurityException.class, () -> parser.evaluate("f(4)", Map.of("f", (ExpressionFunction) args -> 0)));
        assertEquals(7, parser.evaluate("x.value", Map.of("x", Map.of("value", 7))));
    }
}
