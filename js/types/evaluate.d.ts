import { Expression } from './expression.js';
import { Instruction, Value, ValueObject } from './instruction.js';
export type OpFunc<T = Value> = {
    (...args: T[]): T;
    (a: T): T;
    (a: T, b: T): T;
    (a: T, b: T, c: T): T;
};
export declare class SecurityError extends Error {
}
export default function evaluate(tokens: Instruction | Instruction[], expr: Expression, values: ValueObject): Value;
