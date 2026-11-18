import { ValueObject } from './instruction.js';
export type Primitive = string | number | boolean | null | undefined;
/**
 * If you want these utilities to be usable inside your parser with your `Value` type,
 * replace `unknown` with your `Value` and tighten the function signatures accordingly.
 */
export type ValueLike = unknown;
export declare function add(a: ValueLike, b: ValueLike): number;
export declare function sub(a: number, b: number): number;
export declare function mul(a: number, b: number): number;
export declare function div(a: number, b: number): number;
export declare function mod(a: number, b: number): number;
export declare function concat(a: ValueLike, b: ValueLike): ValueLike;
export declare function equal(a: ValueLike, b: ValueLike): boolean;
export declare function notEqual(a: ValueLike, b: ValueLike): boolean;
export declare function greaterThan(a: number, b: number): boolean;
export declare function lessThan(a: number, b: number): boolean;
export declare function greaterThanEqual(a: number, b: number): boolean;
export declare function lessThanEqual(a: number, b: number): boolean;
export declare function andOperator(a: ValueLike, b: ValueLike): boolean;
export declare function orOperator(a: ValueLike, b: ValueLike): boolean;
export declare function inOperator(a: ValueLike, b: ValueLike): boolean;
export declare function sinh(a: number): number;
export declare function cosh(a: number): number;
export declare function tanh(a: number): number;
export declare function asinh(a: number): number;
export declare function acosh(a: number): number;
export declare function atanh(a: number): number;
export declare function log10(a: number): number;
export declare function neg(a: number): number;
export declare function not(a: ValueLike): boolean;
export declare function trunc(a: number): number;
export declare function random(a?: number): number;
export declare function factorial(a: number): number;
export declare function gamma(n: number): number;
export declare function stringOrArrayLength(s: ValueLike): number;
export declare function hypot(...values: number[]): number;
export declare function condition<T>(cond: ValueLike, yep: T, nope: T): T;
/**
 * Decimal adjustment of a number.
 * From @escopecz.
 *
 * @param value The number.
 * @param exp   The exponent (the 10 logarithm of the adjustment base).
 * @return The adjusted value.
 */
export declare function roundTo(value: number, exp?: number): number;
export declare function setVar(name: string, value: ValueObject[keyof ValueObject], variables?: ValueObject): ValueLike;
export declare function arrayIndex<T>(array: T[], index: number): T;
export declare function max(): number;
export declare function max(array: number[]): number;
export declare function max(a: number, ...args: number[]): number;
export declare function min(): number;
export declare function min(array: number[]): number;
export declare function min(a: number, ...args: number[]): number;
export declare function arrayMap<T, R>(f: (x: T, i: number) => R, a: T[]): R[];
export declare function arrayFold<T, R>(f: (acc: R, x: T, i: number) => R, init: R, a: T[]): R;
export declare function arrayFilter<T>(f: (x: T, i: number) => boolean, a: T[]): T[];
export declare function stringOrArrayIndexOf(target: string, s: string): number;
export declare function stringOrArrayIndexOf<T>(target: T, s: readonly T[]): number;
export declare function arrayJoin(sep: string, a: unknown[]): string;
export declare function sign(x: number): number;
export declare function cbrt(x: number): number;
export declare function expm1(x: number): number;
export declare function log1p(x: number): number;
export declare function log2(x: number): number;
export declare function sum(array: unknown[]): number;
