import exprEval, {
  Expression,
  Instruction,
  Parser,
  type ExpressionFunc,
  type InstructionValue,
  type ParserOptions,
  type Value,
  type ValueObject,
} from "@expr-eval/js";

const options: ParserOptions = { operators: { array: false } };
const parser: Parser = new exprEval.Parser(options);
const scope: ValueObject = { x: 3 };
const parsed: Expression = parser.parse("x * 2");
const instruction: Instruction = parsed.tokens[0];
const value: Value = parsed.evaluate(scope);
const callable = parsed.toJSFunction(["x"] as const);

const twice: ExpressionFunc<[number], number> = (x) => x * 2;
parser.functions.twice = twice;
parser.functions = { twice, later: async () => 3 };
const callback: ExpressionFunc = twice;
callback(2);
// @ts-expect-error twice requires a number
twice("2");
const promise: Value = Promise.resolve(3);
delete parser.functions.fac;
parser.consts.radius = 2;
parser.consts = {};
parser.unaryOps = { twice };

interface Coordinates {
  x: number;
}
class Point {
  x = 3;
}
const position: Coordinates = { x: 5 };
parsed.evaluate(position);
parser.evaluate("p.x", { p: new Point(), xs: [1, 2] as const });

const index = parsed.binaryOps["["];
if (index) {
  const char: string | undefined = index("abc", 1);
  const item: number | undefined = index([1, 2] as const, 10);
  // @ts-expect-error out-of-bounds access can return undefined
  const guaranteed: number = index([1, 2], 10);
  void [char, item, guaranteed];
}
const joined: number[] | undefined = parsed.binaryOps["||"]?.([1], [2]);
const choice = parsed.functions.if?.(true, 3, "fallback");
parsed.functions.max?.([1, 2] as const);

const zero: 0 = new Instruction("INUMBER", null).value;
const omitted: 0 = new Instruction("INUMBER").value;
const name: InstructionValue<"IVAR"> = "x";
const token: Instruction<string> = new Instruction("IVAR", name);
// @ts-expect-error a nested expression requires instructions
new Instruction("IEXPR", 3);
void [
  instruction,
  value,
  callable(4),
  promise,
  joined,
  choice,
  zero,
  omitted,
  token,
];
