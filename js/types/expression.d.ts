import { Parser } from './parser.js';
import { Instruction, Value, ValueObject } from './instruction.js';
export declare class Expression {
    /** The current editable instruction array. Assign it back to apply edits to the expression. */
    tokens: Instruction[];
    parser: Parser;
    get unaryOps(): {
        sin: (x: number) => number;
        cos: (x: number) => number;
        tan: (x: number) => number;
        asin: (x: number) => number;
        acos: (x: number) => number;
        atan: (x: number) => number;
        sinh: (x: number) => number;
        cosh: (x: number) => number;
        tanh: (x: number) => number;
        asinh: (x: number) => number;
        acosh: (x: number) => number;
        atanh: (x: number) => number;
        sqrt: (x: number) => number;
        cbrt: (x: number) => number;
        log: (x: number) => number;
        log2: (x: number) => number;
        ln: (x: number) => number;
        lg: (x: number) => number;
        log10: (x: number) => number;
        expm1: (x: number) => number;
        log1p: (x: number) => number;
        abs: (x: number) => number;
        ceil: (x: number) => number;
        floor: (x: number) => number;
        round: (x: number) => number;
        trunc: (x: number) => number;
        "-": typeof import('./functions.js').neg;
        "+": NumberConstructor;
        exp: (x: number) => number;
        not: typeof import('./functions.js').not;
        length: typeof import('./functions.js').stringOrArrayLength;
        "!": typeof import('./functions.js').factorial;
        sign: (x: number) => number;
    };
    get binaryOps(): {
        "+": typeof import('./functions.js').add;
        "-": typeof import('./functions.js').sub;
        "*": typeof import('./functions.js').mul;
        "/": typeof import('./functions.js').div;
        "%": typeof import('./functions.js').mod;
        "^": (x: number, y: number) => number;
        "||": typeof import('./functions.js').concat;
        "==": typeof import('./functions.js').equal;
        "!=": typeof import('./functions.js').notEqual;
        ">": typeof import('./functions.js').greaterThan;
        "<": typeof import('./functions.js').lessThan;
        ">=": typeof import('./functions.js').greaterThanEqual;
        "<=": typeof import('./functions.js').lessThanEqual;
        and: typeof import('./functions.js').andOperator;
        or: typeof import('./functions.js').orOperator;
        in: typeof import('./functions.js').inOperator;
        "=": typeof import('./functions.js').setVar;
        "[": typeof import('./functions.js').arrayIndex;
    };
    get ternaryOps(): {
        "?": typeof import('./functions.js').condition;
    };
    get functions(): {
        random: typeof import('./functions.js').random;
        fac: typeof import('./functions.js').factorial;
        min: typeof import('./functions.js').min;
        max: typeof import('./functions.js').max;
        hypot: (...values: number[]) => number;
        pyt: (...values: number[]) => number;
        pow: (x: number, y: number) => number;
        atan2: (y: number, x: number) => number;
        if: typeof import('./functions.js').condition;
        gamma: typeof import('./functions.js').gamma;
        roundTo: typeof import('./functions.js').roundTo;
        map: typeof import('./functions.js').arrayMap;
        fold: typeof import('./functions.js').arrayFold;
        filter: typeof import('./functions.js').arrayFilter;
        indexOf: typeof import('./functions.js').stringOrArrayIndexOf;
        join: typeof import('./functions.js').arrayJoin;
        sum: typeof import('./functions.js').sum;
    } & {
        [key: string]: import('./evaluate.js').OpFunc;
    };
    constructor(tokens: Instruction[], parser: Parser);
    simplify(values?: ValueObject): Expression;
    substitute(variable: string, expr: Expression | string | number): Expression;
    evaluate(values?: ValueObject): Value;
    toString(): string;
    symbols(options?: {
        withMembers?: boolean;
    }): string[];
    variables(options?: {
        withMembers?: boolean;
    }): string[];
    toJSFunction(param?: string, variables?: ValueObject): (...args: Value[]) => Value;
}
