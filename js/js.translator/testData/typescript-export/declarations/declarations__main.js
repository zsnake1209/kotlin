"use strict";
var foo = JS_TESTS.foo;
var varargInt = JS_TESTS.foo.varargInt;
var varargNullableInt = JS_TESTS.foo.varargNullableInt;
var varargWithOtherParameters = JS_TESTS.foo.varargWithOtherParameters;
var varargWithComplexType = JS_TESTS.foo.varargWithComplexType;
var sumNullable = JS_TESTS.foo.sumNullable;
var defaultParameters = JS_TESTS.foo.defaultParameters;
var generic1 = JS_TESTS.foo.generic1;
var generic2 = JS_TESTS.foo.generic2;
var generic3 = JS_TESTS.foo.generic3;
var inlineFun = JS_TESTS.foo.inlineFun;
var _const_val = JS_TESTS.foo._const_val;
var _val = JS_TESTS.foo._val;
var _var = JS_TESTS.foo._var;
var A = JS_TESTS.foo.A;
var A1 = JS_TESTS.foo.A1;
var A2 = JS_TESTS.foo.A2;
var A3 = JS_TESTS.foo.A3;
function assert(condition) {
    if (!condition) {
        throw "Assertion failed";
    }
}
function box() {
    assert(foo.sum(10, 20) === 30);
    assert(varargInt(new Int32Array([1, 2, 3])) === 3);
    assert(varargNullableInt([10, 20, 30, null, undefined, 40]) === 6);
    assert(varargWithOtherParameters("1234", ["1", "2", "3"], "12") === 9);
    assert(varargWithComplexType([]) === 0);
    assert(varargWithComplexType([
        function (x) { return x; },
        function (x) { return [new Int32Array([1, 2, 3])]; },
        function (x) { return []; },
    ]) === 3);
    assert(sumNullable(10, null) === 10);
    assert(sumNullable(undefined, 20) === 20);
    assert(sumNullable(1, 2) === 3);
    assert(defaultParameters(20, "OK") === "20OK");
    assert(generic1("FOO") === "FOO");
    assert(generic1({ x: 10 }).x === 10);
    assert(generic2(null) === true);
    assert(generic2(undefined) === true);
    assert(generic2(10) === false);
    assert(generic3(10, true, "__", {}) === null);
    var result = 0;
    inlineFun(10, function (x) { result = x; });
    assert(result === 10);
    assert(_const_val === 1);
    assert(_val === 1);
    assert(_var === 1);
    new A();
    assert(new A1(10).x === 10);
    assert(new A2("10", true).x === "10");
    assert(new A3().x === 100);
    return "OK";
}
