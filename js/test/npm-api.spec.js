import assert from "node:assert/strict";
import api, { Expression, Instruction, Parser } from "@expr-eval/js";

describe("npm consumer API", () => {
  it("honors public constructors", () => {
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
    assert.equal(expr.evaluate({ x: 3 }), 6);
    assert.equal(new Instruction("IEXPR").toString(), "Invalid Instruction");
  });

  it("returns a variadic, nonconstructible function from toJSFunction", () => {
    const fn = Parser.parse("x+y").toJSFunction("x,y");
    assert.equal(fn(2, 3), 5);
    assert.equal(fn.length, 0);
    assert.throws(() => new fn(2, 3), TypeError);
  });

  it("uses numeric addition in evaluate and toJSFunction", () => {
    const expr = Parser.parse("x + 1");
    const addOne = expr.toJSFunction("x");
    for (const [x, expected] of [
      ["2", 3],
      [[2], 3],
      [null, 1],
      ["abc", NaN],
    ]) {
      assert.equal(expr.evaluate({ x }), expected);
      assert.equal(addOne(x), expected);
    }
    assert.equal(expr.toJSFunction("", { x: "2" })(), 3);
    assert.equal(Parser.parse("x || 1").toJSFunction("x")("2"), "21");
  });

  it("uses the parser's addition operator inside compiled function definitions", () => {
    const parser = new Parser();
    const expr = parser.parse("(sumPair(a,b) = a+b)(x,y)");
    const compiled = expr.toJSFunction("x,y,sumPair");
    assert.equal(compiled("2", "1"), 3);
    parser.binaryOps["+"] = (a, b) => [a, b];
    assert.deepEqual(expr.evaluate({ x: "2", y: 1 }), ["2", 1]);
    assert.deepEqual(compiled("2", 1), ["2", 1]);
  });

  it("keeps variable names from public instructions available in compiled addition", () => {
    const variable = "exprEval$add";
    const tokens = [
      new Instruction("IVAR", variable),
      new Instruction("INUMBER", 1),
      new Instruction("IOP2", "+"),
    ];
    const expr = new Expression(tokens, new Parser());
    assert.equal(expr.evaluate({ [variable]: "2" }), 3);
    assert.equal(expr.toJSFunction(variable)("2"), 3);
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

  it("isolates registry edits from existing, future and static parsers", () => {
    const parser = new Parser();
    const existing = new Parser();
    const registries = [
      "unaryOps",
      "binaryOps",
      "ternaryOps",
      "functions",
      "consts",
    ];
    for (const name of registries) {
      assert.notEqual(parser[name], existing[name], name);
    }
    parser.consts.PI = 4;
    delete parser.functions.fac;
    parser.binaryOps["+"] = () => 42;
    assert.equal(parser.evaluate("PI"), 4);
    assert.equal(parser.evaluate("1+1"), 42);
    for (const other of [existing, new Parser(), Parser]) {
      assert.equal(other.evaluate("PI"), Math.PI);
      assert.equal(other.evaluate("fac(3) + 1"), 7);
    }
  });

  it("keeps definitions callable in their scope without growing the registry", () => {
    const parser = new Parser();
    const entries = Object.keys(parser.functions);
    const scope = {};
    for (let i = 0; i < 3; i++) {
      parser.evaluate("f(x)=x+1", scope);
      assert.equal(parser.evaluate("f(2)", scope), 3);
    }
    assert.deepEqual(Object.keys(parser.functions), entries);
  });

  it("requires registration to use a scope's function on a different parser", () => {
    const parser = new Parser();
    const other = new Parser();
    const scope = { factor: 3 };
    const f = parser.evaluate("f(x)=x*factor", scope);
    scope.factor = 4;
    assert.equal(f(3), 12);
    assert.equal(parser.evaluate("f(2)", scope), 8);
    assert.throws(() => other.evaluate("f(2)", scope), /unallowed function/);
    other.functions.f = f;
    assert.equal(other.evaluate("f(2)"), 8);
  });

  it("uses the parser's current function registry for parsed expressions", () => {
    const parser = new Parser();
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
