// eslint-disable-next-line @typescript-eslint/no-require-imports -- Exercise the CommonJS declaration entry.
import pkg = require("@expr-eval/js");

const options: pkg.ParserOptions = { operators: { array: false } };
const parser: pkg.Parser = new pkg.default.Parser(options);
const expr: pkg.Expression = parser.parse("x+1");
const instruction: pkg.Instruction = new pkg.Instruction("IVAR", "x");
const value: pkg.Value = expr.toJSFunction(["x"])(2);
void [instruction, value];
