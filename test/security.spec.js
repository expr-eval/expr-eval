import { assert } from "vitest";
import { Parser } from "@/parser";
import { SecurityError } from "@/evaluate";

class UnexpectedError extends Error {
  constructor() {
    super("TEST ERROR!");
  }
}

/* A context of potentially dangerous stuff */
var context = {
  write() {
    throw new UnexpectedError();
  },
  cmd() {
    throw new UnexpectedError();
  },
  exec() {
    throw new UnexpectedError();
  },
  evalFunc() {
    throw new UnexpectedError();
  },
  FunctionConstructor() {
    throw new UnexpectedError();
  },
};

describe("Security tests", function () {
  it("should fail on direct function call to an unallowed function", function () {
    var parser = new Parser();
    assert.throws(() => {
      parser.evaluate('write("pwned.txt","Hello!")', context);
    }, SecurityError);
  });

  it("should allow IFUNDEF but keep function calls safe", function () {
    var parserWithFndef = new Parser({
      operators: { fndef: true },
    });
    var safeExpr = "(f(x) = x * x)(5)";
    assert.strictEqual(
      parserWithFndef.evaluate(safeExpr),
      25,
      "Should correctly evaluate an expression with an allowed IFUNDEF.",
    );
    var dangerousExpr = '((h(x) = write("pwned.txt", x)) + h(5))';
    assert.throws(() => {
      parserWithFndef.evaluate(dangerousExpr, context);
    }, SecurityError);
  });

  it("should fail when a variable is assigned a dangerous function", function () {
    var parser = new Parser();

    var dangerousContext = { ...context, evil: context.cmd };

    assert.throws(() => {
      parser.evaluate('evil("ls -lh /")', dangerousContext);
    }, SecurityError);
  });

  it("PoC provided by researcher VU#263614 deny child exec process", function () {
    var parser = new Parser();
    assert.throws(() => {
      parser.evaluate('exec("whoami")', context);
    }, SecurityError);
  });

  it("PoC provided by researcher https://github.com/silentmatt/expr-eval/issues/289 by gitHub @baoquanh", function () {
    var contextWrapper = {
      test: context,
    };

    var parser = new Parser();
    assert.throws(() => {
      parser.evaluate('test.write("pwned.txt","Hello!")', contextWrapper);
    }, SecurityError);
  });

  describe("Prototype pollution and member function protection (lines 173-182)", function () {
    it("should block __proto__ and prototype pollution attempts", function () {
      var parser = new Parser();

      assert.throws(() => {
        parser.evaluate("obj.__proto__", { obj: {} });
      }, SecurityError);

      assert.throws(() => {
        parser.evaluate("obj.prototype", { obj: {} });
      }, SecurityError);

      assert.throws(() => {
        parser.evaluate("user.config.__proto__.isAdmin = true", {
          user: { config: {} },
        });
      }, SecurityError);
    });

    it("should block dangerous function calls via member access but allow safe Math functions", function () {
      var parser = new Parser();

      assert.throws(() => {
        parser.evaluate('obj.write("evil.txt", "data")', { obj: context });
      }, SecurityError);

      assert.throws(() => {
        parser.evaluate('obj.cmd("whoami")', { obj: context });
      }, SecurityError);

      var safe = {
        absolute: Math.abs,
        squareRoot: Math.sqrt,
      };
      assert.strictEqual(parser.evaluate("obj.absolute(-5)", { obj: safe }), 5);
      assert.strictEqual(
        parser.evaluate("obj.squareRoot(16)", { obj: safe }),
        4,
      );
    });

    it("should block eval and Function constructor but allow registered custom functions", function () {
      var parser = new Parser();

      assert.throws(() => {
        parser.evaluate('obj.evalFunc("malicious()")', { obj: context });
      }, SecurityError);

      assert.throws(() => {
        parser.evaluate('obj.FunctionConstructor("return process")()', {
          obj: context,
        });
      }, SecurityError);

      // @ts-expect-error
      var customFunc = function (x) {
        return x * 2;
      };
      parser.functions.double = customFunc;
      var obj = { myDouble: customFunc };
      assert.strictEqual(parser.evaluate("obj.myDouble(5)", { obj: obj }), 10);
    });
  });
});
