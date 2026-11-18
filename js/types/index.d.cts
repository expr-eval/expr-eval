import type {
  BinaryOps,
  Constants,
  Functions,
  OperatorOption,
  TernaryOps,
  UnaryOps,
} from "./registries.cjs";

/** A native JavaScript value, including host objects, functions and promises. */
export type Value =
  string | number | bigint | boolean | symbol | null | undefined | object;
export type ValueObject = Record<string, Value>;
/** Supply an argument tuple and result type for a callback with a known signature. */
export type ExpressionFunc<Args extends unknown[] = any[], Result = Value> = (
  ...args: Args
) => Result;

interface InstructionPayloads {
  INUMBER: Value;
  IOP1: string;
  IOP2: string;
  IOP3: string;
  IVAR: string;
  IVARNAME: string;
  IFUNCALL: number;
  IFUNDEF: number;
  IEXPR: readonly Instruction[];
  IEXPREVAL: Value;
  IMEMBER: string;
  IENDSTATEMENT: 0;
  IARRAY: number;
}
export type InstructionType = keyof InstructionPayloads;
export type InstructionValue<K extends InstructionType = InstructionType> =
  InstructionPayloads[K];

type Stored<T> = T extends null | undefined ? 0 : T;
export interface Instruction<
  TValue extends Value = Value,
  K extends InstructionType = InstructionType,
> {
  type: K;
  value: TValue;
  toString(): string | TValue;
}
// Keep the opcode and payload paired even when callers supply an explicit value type.
type InstructionArgs<T> =
  | (0 extends Stored<T> ? [type: InstructionType] : never)
  | {
      [K in InstructionType]: [
        type: K,
        value: T & (InstructionValue<K> | null | undefined),
      ];
    }[InstructionType];

type StoredArgument<A> = A extends [InstructionType, infer V extends Value]
  ? Stored<V>
  : 0;

export declare const Instruction: {
  readonly prototype: Instruction;
  new <
    T extends Value = Value,
    A extends InstructionArgs<T> = InstructionArgs<T>,
  >(
    ...args: A
  ): Instruction<StoredArgument<A>, A[0]>;
};

export interface ParserOptions {
  allowMemberAccess?: boolean;
  /** Custom operators can be enabled or disabled by name. */
  operators?: Partial<Record<OperatorOption, boolean>> &
    Record<string, boolean | undefined>;
}

export declare class Parser {
  options: ParserOptions;
  unaryOps: UnaryOps;
  binaryOps: BinaryOps;
  ternaryOps: TernaryOps;
  functions: Functions;
  consts: Constants;
  constructor(options?: ParserOptions);
  parse(expression: string): Expression;
  evaluate(expression: string, variables?: object): Value;
  static parse(expression: string): Expression;
  static evaluate(expression: string, variables?: object): Value;
  isOperatorEnabled(op: string): boolean;
}

interface SymbolOptions {
  withMembers?: boolean;
}
export declare class Expression {
  /** The current editable instruction array. Assign it back to apply edits to the expression. */
  tokens: Instruction[];
  parser: Parser;
  get unaryOps(): Parser["unaryOps"];
  get binaryOps(): Parser["binaryOps"];
  get ternaryOps(): Parser["ternaryOps"];
  get functions(): Parser["functions"];
  constructor(tokens: Instruction[], parser: Parser);
  simplify(values?: object): Expression;
  substitute(
    variable: string,
    expression: Expression | string | number,
  ): Expression;
  evaluate(values?: object): Value;
  toString(): string;
  symbols(options?: SymbolOptions): string[];
  variables(options?: SymbolOptions): string[];
  toJSFunction(
    param?: string | readonly string[],
    variables?: object,
  ): (...args: Value[]) => Value;
}

declare const api: { Parser: typeof Parser; Expression: typeof Expression };
export default api;
