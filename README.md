# expr-eval

Parse and evaluate mathematical expressions with the same syntax on JavaScript and the JVM.

- [JavaScript and TypeScript package](js/README.md)
- [Kotlin and Java library](jvm/README.md)

Run both suites from the repository root:

```sh
nix develop                  # optional when Java 17+ and the Node version in js/.nvmrc are installed
./gradlew :jvm:build
npm --prefix js ci
npm --prefix js test
```
