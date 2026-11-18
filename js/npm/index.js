import {
  Parser,
  Expression,
  Instruction,
} from "../../library/build/compileSync/js/main/productionLibrary/kotlin/expr-eval.mjs";

const defaultParser = new Parser();
Parser.parse = (source) => defaultParser.parse(source);
Parser.evaluate = (source, values) => defaultParser.evaluate(source, values);

export { Parser, Expression, Instruction };
export default { Parser, Expression };
