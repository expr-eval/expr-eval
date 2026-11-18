import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { cwd } from "node:process";
import { Parser } from "@expr-eval/js";

const cases = readFileSync(resolve(cwd(), "../parity/bind.tsv"), "utf8")
  .trimEnd()
  .split("\n")
  .map((line) => line.split("\t"));

describe("Function.prototype.bind cross-runtime parity", () => {
  for (const [mode, input, type, expected = ""] of cases) {
    it(mode + ": " + input, () => {
      const expression = new Parser().parse(input);
      const run = () =>
        mode === "evaluate"
          ? expression.evaluate()
          : expression.toJSFunction()();
      if (type === "error") {
        assert.throws(run, (error) => error.message === expected);
      } else {
        const actual = run();
        if (type === "function") assert.equal(typeof actual, "function");
        else if (type === "array")
          assert.deepEqual(actual, expected.split(",").map(Number));
        else if (type === "number") assert.deepEqual(actual, Number(expected));
        else if (type === "boolean") assert.equal(actual, expected === "true");
        else assert.equal(actual, expected);
      }
    });
  }
});
