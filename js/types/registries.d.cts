import type { ExpressionFunc, Value } from "./index.cjs";

// Each parser starts with the built-ins. Consumers can delete them, register
// their own functions, or replace a registry with an entirely new object.
type Registry<Builtins> = Partial<Builtins> &
  Record<string, ExpressionFunc | undefined>;

type MathUnaryName =
  | "sin"
  | "cos"
  | "tan"
  | "asin"
  | "acos"
  | "atan"
  | "sinh"
  | "cosh"
  | "tanh"
  | "asinh"
  | "acosh"
  | "atanh"
  | "sqrt"
  | "cbrt"
  | "log"
  | "log2"
  | "ln"
  | "lg"
  | "log10"
  | "expm1"
  | "log1p"
  | "abs"
  | "ceil"
  | "floor"
  | "round"
  | "trunc"
  | "exp"
  | "sign";

type UnaryMath = (value: number) => number;
type BinaryMath = (left: number, right: number) => number;
type Condition = <Y, N>(condition: unknown, yes: Y, no: N) => Y | N;

export type OperatorOption =
  | MathUnaryName
  | "length"
  | "add"
  | "comparison"
  | "concatenate"
  | "conditional"
  | "divide"
  | "factorial"
  | "logical"
  | "multiply"
  | "power"
  | "remainder"
  | "subtract"
  | "in"
  | "assignment"
  | "array"
  | "fndef";

export type UnaryOps = Registry<
  Record<MathUnaryName | "-" | "!", UnaryMath> & {
    "+": NumberConstructor;
    not: (value: unknown) => boolean;
    length: (value: unknown) => number;
  }
>;

type Primitive = Exclude<Value, object>;
interface Concat {
  <L, R>(left: readonly L[], right: readonly R[]): (L | R)[];
  (left: Primitive, right: unknown): string;
  (left: unknown, right: Primitive): string;
  (left: unknown, right: unknown): string | unknown[];
}
interface Index {
  (value: string, index: number): string | undefined;
  <T>(value: { readonly [index: number]: T }, index: number): T | undefined;
}

export type BinaryOps = Registry<
  Record<"-" | "*" | "/" | "%" | "^", BinaryMath> &
    Record<
      "==" | "!=" | ">" | "<" | ">=" | "<=" | "and" | "or",
      (left: unknown, right: unknown) => boolean
    > & {
      "+": (left: unknown, right: unknown) => number;
      "||": Concat;
      in: (value: unknown, collection: string | readonly unknown[]) => boolean;
      "=": <T>(name: string, value: T, variables?: object) => T;
      "[": Index;
    }
>;

export type TernaryOps = Registry<{ "?": Condition }>;

interface Extrema {
  (values: readonly number[]): number;
  (...values: number[]): number;
}
interface Filter {
  <T, S extends T>(
    predicate: (value: T, index: number) => value is S,
    values: readonly T[],
  ): S[];
  <T>(
    predicate: (value: T, index: number) => unknown,
    values: readonly T[],
  ): T[];
}

export type Functions = Registry<{
  random: (limit?: number) => number;
  fac: UnaryMath;
  gamma: UnaryMath;
  min: Extrema;
  max: Extrema;
  hypot: (...values: number[]) => number;
  pyt: (...values: number[]) => number;
  pow: BinaryMath;
  atan2: (y: number, x: number) => number;
  if: Condition;
  roundTo: (value: number, exponent?: number) => number;
  map: <T, R>(fn: (value: T, index: number) => R, values: readonly T[]) => R[];
  fold: <T, R>(
    fn: (acc: R, value: T, index: number) => R,
    initial: R,
    values: readonly T[],
  ) => R;
  filter: Filter;
  indexOf: (target: unknown, sequence: string | readonly unknown[]) => number;
  join: (separator: string | undefined, values: readonly unknown[]) => string;
  sum: (values: readonly unknown[]) => number;
}>;

export type Constants = Record<string, Value> &
  Partial<{
    E: number;
    PI: number;
    true: boolean;
    false: boolean;
  }>;
