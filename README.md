# expr-eval

The npm and JVM packages evaluate mathematical expressions with one Kotlin Multiplatform implementation in `library/src/commonMain`.

- [JavaScript and TypeScript package](js/README.md)
- [Kotlin and Java library](library/README.md)

Run both suites from the repository root:

```sh
nix develop                  # optional when Java 17+ and the Node version in js/.nvmrc are installed
./gradlew :library:allTests
npm --prefix js ci
npm --prefix js test
```
