# expr-eval for Kotlin and Java

The JVM implementation supports expr-eval syntax, including numeric and string
operators, lazy `and`, `or`, and `?:`, arrays, named functions, assignments,
and statements. See the [expression language guide](../js/README.md) for syntax
and built-in functions.

```java
import java.util.Map;
import org.expreval.Parser;

var parser = new Parser();
var expression = parser.parse("2 * x + 1");
Object value = expression.evaluate(Map.of("x", 3)); // 7.0
var callable = expression.toFunction("x");
Object other = callable.call(4); // 9.0
```

```kotlin
import org.expreval.Parser

val expression = Parser.parse("2 * x + 1")
val value = expression.evaluate(mapOf("x" to 3)) // 7.0
```

Use a mutable map to observe assignments and function definitions made by an
expression. Member access works on maps, lists, strings, and arrays; it never
reflects over arbitrary JVM objects. Missing members and out of range indexes
return `Undefined.INSTANCE` in Java (`Undefined` in Kotlin). An unresolved
variable throws `IllegalArgumentException`.

Register host functions on the parser that should be able to invoke them:

```java
parser.getFunctions().put("twice", args -> 2 * ((Number) args[0]).doubleValue());
Object result = parser.evaluate("twice(21)"); // 42.0
```

The JVM counterpart of JavaScript's `toJSFunction` is `toFunction`. It
creates an `ExpressionFunction` from a comma-separated parameter string or list of parameter names and
optional simplification bindings. It follows the callable form of the language (for example, string addition) and does not
generate JavaScript or access JavaScript globals. For the default parser in
Java, use `Parser.Companion.parse(...)` or create a `Parser` instance.

The project targets Java 17. For local Maven consumption, run
`./gradlew :jvm:publishToMavenLocal`, then use
`io.github.expr-eval:expr-eval:3.8.1-SNAPSHOT`.

To prepare a release, configure a GPG signing key and select it via
`signing.gnupg.keyName` in your Gradle properties if needed. Run:

```sh
./gradlew :jvm:centralBundle -PreleaseVersion=3.8.1 -PcentralSigning=true
```

This creates a signed archive under `jvm/build/distributions/` with the binary,
sources, API docs, POM, and checksums. A maintainer with the verified
`io.github.expr-eval` namespace can [upload the archive through Central Portal](https://central.sonatype.org/publish/publish-portal-upload/).
The Gradle task only writes local files.
