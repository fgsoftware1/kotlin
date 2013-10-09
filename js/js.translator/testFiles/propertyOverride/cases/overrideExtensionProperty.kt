package foo

class T

open class A {
    open val T.foo: Int = 34
        get() {
            return $foo
        }
    fun test(): Int {
        return T().foo
    }
}

class B : A() {
    override val T.foo: Int = 5
}


fun box(): String {
    if (A().test() != 34) return "A().test() != 34, it: ${A().test() != 34}"
    if (B().test() != 5) return "B().test() != 5, it: ${B().test() != 5}"

    return "OK"
}