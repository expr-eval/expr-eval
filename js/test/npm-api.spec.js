import assert from "node:assert/strict";
import api, { Expression, Instruction, Parser } from "@expr-eval/js";

describe("npm consumer API", () => {
  it("honors public constructors and mutable instructions", () => {
    assert.equal(api.Parser, Parser);
    assert.equal(api.Expression, Expression);
    const parser = new Parser();
    assert.deepEqual(Object.keys(parser).sort(), [
      "binaryOps",
      "consts",
      "functions",
      "options",
      "ternaryOps",
      "unaryOps",
    ]);
    const expr = new Expression(parser.parse("3 + x").tokens, parser);
    assert.ok(expr.tokens[0] instanceof Instruction);
    assert.equal(expr.tokens[0].toString(), 3);
    expr.tokens[0].value = 4;
    assert.equal(expr.evaluate({ x: 3 }), 7);
    assert.equal(new Instruction("IEXPR").toString(), "Invalid Instruction");
  });

  it("returns a variadic, nonconstructible function from toJSFunction", () => {
    const fn = Parser.parse("x+y").toJSFunction("x,y");
    assert.equal(fn(2, 3), 5);
    assert.equal(fn.length, 0);
    assert.throws(() => new fn(2, 3), TypeError);
  });

  it("preserves built-in aliases and their public arities", () => {
    const parser = new Parser();
    assert.equal(parser.functions.fac, parser.unaryOps["!"]);
    assert.equal(parser.functions.if, parser.ternaryOps["?"]);
    assert.equal(parser.functions.pyt, Math.hypot);
    assert.equal(parser.functions.map.length, 2);
    assert.equal(parser.functions.fold.length, 3);
    assert.equal(parser.binaryOps["="].length, 3);
    assert.equal(parser.unaryOps.length.length, 1);
  });

  it("uses the JavaScript random source for each draw", () => {
    const original = Math.random;
    try {
      Math.random = () => 0.25;
      assert.equal(Parser.evaluate("random(8)"), 2);
      Math.random = () => 0.5;
      assert.equal(Parser.evaluate("random()"), 0.5);
    } finally {
      Math.random = original;
    }
  });

  it("reads current scope values and preserves native object identity", () => {
    const parser = new Parser();
    const values = { a: [], obj: {}, nil: null };
    assert.equal(parser.evaluate("a", values), values.a);
    assert.equal(parser.evaluate("obj", values), values.obj);
    assert.equal(parser.evaluate("nil", values), null);
    assert.equal(parser.evaluate("obj.missing", values), undefined);
    const result = parser.evaluate("a=[2,3]", values);
    assert.equal(result, values.a);
    result[0] = 7;
    assert.equal(parser.evaluate("a[0]", values), 7);
  });

  it("uses live caller scopes and function registries", () => {
    const parser = new Parser();
    const scope = { factor: 3 };
    const f = parser.evaluate("f(x)=x*factor", scope);
    scope.factor = 4;
    assert.equal(f(3), 12);
    assert.equal(new Parser().evaluate("f(2)", scope), 8);
    const expr = parser.parse("custom(2)");
    parser.functions = { custom: (x) => x * 3 };
    assert.equal(expr.evaluate(), 6);
    parser.functions.custom = (x) => x + 1;
    assert.equal(expr.evaluate(), 3);
  });
});

it("passes the caller's scope to registered assignment operators", () => {
  const parser = new Parser();
  const values = {};
  parser.binaryOps = {
    ...parser.binaryOps,
    "=": (name, value, scope) => {
      assert.equal(scope, values);
      scope[name] = value;
      return value;
    },
  };
  assert.equal(parser.evaluate("x=2", values), 2);
  assert.equal(values.x, 2);
});

it("reads native properties through registered functions and object getters", () => {
  const parser = new Parser();
  class Point {
    get x() {
      return 3;
    }
  }
  assert.equal(parser.evaluate("p.x", { p: new Point() }), 3);
  assert.equal(parser.evaluate("(sin).name"), Math.sin.name);
});
