export declare const INUMBER: "INUMBER";
export declare const IOP1: "IOP1";
export declare const IOP2: "IOP2";
export declare const IOP3: "IOP3";
export declare const IVAR: "IVAR";
export declare const IVARNAME: "IVARNAME";
export declare const IFUNCALL: "IFUNCALL";
export declare const IFUNDEF: "IFUNDEF";
export declare const IEXPR: "IEXPR";
export declare const IEXPREVAL: "IEXPREVAL";
export declare const IMEMBER: "IMEMBER";
export declare const IENDSTATEMENT: "IENDSTATEMENT";
export declare const IARRAY: "IARRAY";
export type InstructionType = typeof INUMBER | typeof IOP1 | typeof IOP2 | typeof IOP3 | typeof IVAR | typeof IVARNAME | typeof IFUNCALL | typeof IFUNDEF | typeof IEXPR | typeof IEXPREVAL | typeof IMEMBER | typeof IENDSTATEMENT | typeof IARRAY;
export type InstructionValue = string | number | boolean | Array<unknown> | unknown | null | undefined;
type Primitive = string | number | boolean | null | undefined;
type ValueBase = Primitive | ValueObject | ValueArray | ExpressionFunc;
export type ValueObject = {
    [key: string]: ValueBase;
};
type ValueArray = ValueBase[];
export type ExpressionFunc = (...args: never[]) => Value | Promise<Value>;
export type Value = ValueBase | ExpressionFunc | Instruction | Instruction[];
export declare class Instruction<TValue extends Value = Value> {
    type: InstructionType;
    value?: TValue | undefined;
    constructor(type: InstructionType, value?: TValue | undefined);
    toString(): string | TValue | undefined;
}
export declare function unaryInstruction(value: Value): Instruction<string | number | boolean | ValueObject | ValueArray | ExpressionFunc | Instruction<Value> | Instruction<Value>[] | null>;
export declare function binaryInstruction(value: Value): Instruction<string | number | boolean | ValueObject | ValueArray | ExpressionFunc | Instruction<Value> | Instruction<Value>[] | null>;
export declare function ternaryInstruction(value: Value): Instruction<string | number | boolean | ValueObject | ValueArray | ExpressionFunc | Instruction<Value> | Instruction<Value>[] | null>;
export {};
