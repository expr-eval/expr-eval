package org.expreval

@JsModule("node:fs")
private external object Fs {
    fun readFileSync(path: String, encoding: String): String
}

internal actual fun bindFixture(): String =
    Fs.readFileSync(js("process.env.EXPR_BIND_FIXTURE") as String, "utf8")
