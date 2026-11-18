import { Expression } from './expression.js';
import { Parser } from './parser.js';
export { Expression, Parser };
export { Instruction } from './instruction.js';
export type { Value, ValueObject, InstructionType, InstructionValue, ExpressionFunc, } from './instruction.js';
declare const _default: {
    Parser: typeof Parser;
    Expression: typeof Expression;
};
export default _default;
