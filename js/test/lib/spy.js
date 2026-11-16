/**
 * A function wrapper that tracks whether it has been called.
 * @template {(...args: any[]) => any} F
 * @typedef {((this: ThisParameterType<F>, ...args: Parameters<F>) => ReturnType<F>) & { called: boolean }} SpyFn
 */

/**
 * @template {(...args: any[]) => any} F
 * @param {F} fn
 * @returns {SpyFn<F>}
 */
export function spy(fn) {
  /** @type {SpyFn<F>} */
  const wrapped = function (...args) {
    wrapped.called = true;
    return fn.apply(this, args);
  };

  wrapped.called = false;
  return wrapped;
}
