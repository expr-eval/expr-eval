package org.expreval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JavaInteropTest {
    @Test void acceptsJavaCallbacksAndCollections() {
        Parser parser = new Parser(new ParserOptions(false, Map.of("multiply", false)));
        ExpressionFunction twice = args -> ((Number) args[0]).doubleValue() * 2;
        parser.getFunctions().put("twice", twice);
        Map<String, Object> scope = new HashMap<>(Map.of("items", new int[]{3}));
        assertEquals(6.0, parser.evaluate("x=twice(items[4294967296])", scope));
        assertEquals(6.0, scope.get("x"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("2*3"));
        assertThrows(SecurityException.class, () ->
            parser.evaluate("f(4)", Map.of("f", (ExpressionFunction) args -> 0)));
    }

    @Test void exposesConstructorsAccessorsAndOverloads() {
        Parser parser = new Parser();
        Expression expr = parser.parse("x+y");
        assertEquals(3, expr.getTokens().size());
        assertSame(parser, expr.getParser());
        assertEquals(List.of("x", "y"), expr.variables());
        assertEquals(7.0, expr.toFunction(List.of("x", "y")).call(3, 4));
        assertEquals(7.0, Parser.Companion.parse("3+4").evaluate());
        assertSame(Undefined.INSTANCE, parser.parse("x").toFunction("x").call());
    }
}
