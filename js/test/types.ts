import exprEval, {
  Expression,
  Instruction,
  Parser,
  type Value,
  type ValueObject,
} from "@expr-eval/js";

const parser: Parser = new exprEval.Parser();
const scope: ValueObject = { x: 3 };
const parsed: Expression = parser.parse("x * 2");
const instruction: Instruction = parsed.tokens[0];
const value: Value = parsed.evaluate(scope);
const callable = parsed.toJSFunction("x");
void [instruction, value, callable(4)];
