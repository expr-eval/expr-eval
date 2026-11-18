package org.expreval

import org.expreval.InstructionType.*

private enum class Kind { END, OP, LITERAL, STRING, PAREN, BRACKET, COMMA, NAME, SEMICOLON }

private data class Token(
    val kind: Kind,
    val value: Any?,
) {
    val text get() = value.toString()

    override fun toString() =
        "T${when (kind) {
            Kind.END -> "EOF"
            Kind.LITERAL -> "NUMBER"
            else -> kind.name
        }}: $text"
}

private class Lexer(
    val parser: Parser,
    val source: String,
) {
    var pos = 0
    private val number = Regex("""(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?""")
    private val hex = Regex("0x[0-9a-fA-F]+")
    private val bin = Regex("0b[01]+")

    private fun Char.cased() = uppercase() != lowercase()

    private fun Char.tail() = this == '_' || this in '0'..'9'

    fun error(message: String): Nothing {
        val prefix = source.substring(0, pos)
        val line = prefix.count { it == '\n' } + 1
        val col = pos - prefix.lastIndexOf('\n')
        throw IllegalArgumentException("parse error [$line:$col]: $message")
    }

    fun next(): Token {
        while (pos < source.length) {
            if (source[pos] in " \t\r\n") {
                pos++
            } else if (source.startsWith("/*", pos)) {
                val end = source.indexOf("*/", pos)
                pos = if (end < 0) source.length else end + 2
            } else {
                break
            }
        }
        if (pos == source.length) return Token(Kind.END, "EOF")

        fun take(
            kind: Kind,
            size: Int,
            value: Any?,
        ): Token {
            pos += size
            return Token(kind, value)
        }
        val c = source[pos]
        if (c in "0123456789.") {
            val radix = if (source.startsWith("0x", pos)) 16 else 2
            val match = (if (radix == 16) hex else bin).matchAt(source, pos)
            if (match != null) {
                return take(
                    Kind.LITERAL,
                    match.value.length,
                    radixNumber(match.value.drop(2), radix),
                )
            }
            number.matchAt(source, pos)?.value?.let {
                return take(Kind.LITERAL, it.length, it.toDouble())
            }
        }
        val pair = source.substring(pos, minOf(pos + 2, source.length))
        val op =
            when {
                pair in pairs -> pair
                c in "+-*/%^?:.><=!" -> c.toString()
                c == '∙' || c == '•' -> "*"
                else -> null
            }
        if (op != null && parser.isOperatorEnabled(op)) return take(Kind.OP, op.length, op)
        if (c == '\'' || c == '"') return quoted(c)
        if (c in "()") return take(Kind.PAREN, 1, c.toString())
        if (c in "[]" && parser.isOperatorEnabled("[")) return take(Kind.BRACKET, 1, c.toString())
        if (c == ',') return take(Kind.COMMA, 1, ",")
        if (c == ';') return take(Kind.SEMICOLON, 1, ";")
        if (c.cased()) {
            var end = pos + 1
            while (end < source.length && (source[end].cased() || source[end].tail())) end++
            val name = source.substring(pos, end)
            if (parser.isOperatorEnabled(name) &&
                (name in parser.unaryOps || name in parser.binaryOps || name in parser.ternaryOps)
            ) {
                return take(Kind.OP, name.length, name)
            }
            while (end < source.length && (source[end].cased() || source[end].tail() || source[end] == '.')) end++
            val constant = source.substring(pos, end)
            if (parser.consts.containsKey(constant)) return take(Kind.LITERAL, constant.length, parser.consts[constant])
            return take(Kind.NAME, name.length, name)
        }
        if (c == '_' || (c == '$' && source.getOrNull(pos + 1)?.cased() == true)) {
            var end = pos + 1
            while (end < source.length && (source[end].cased() || source[end].tail())) end++
            return take(Kind.NAME, end - pos, source.substring(pos, end))
        }
        error("Unknown character \"$c\"")
    }

    private fun quoted(quote: Char): Token {
        val start = pos
        var end = source.indexOf(quote, start + 1)
        while (end >= 0 && source[end - 1] == '\\') end = source.indexOf(quote, end + 1)
        if (end < 0) error("Unknown character \"$quote\"")
        pos = end + 1
        val raw = source.substring(start + 1, end)
        val value =
            buildString {
                var i = 0
                while (i < raw.length) {
                    val c = raw[i++]
                    if (c != '\\') {
                        append(c)
                        continue
                    }
                    when (val escaped = raw.getOrNull(i++)) {
                        '\'', '"', '\\', '/' -> {
                            append(escaped)
                        }

                        'b' -> {
                            append('\b')
                        }

                        'f' -> {
                            append('\u000c')
                        }

                        'n' -> {
                            append('\n')
                        }

                        'r' -> {
                            append('\r')
                        }

                        't' -> {
                            append('\t')
                        }

                        'u' -> {
                            val digits = raw.substring(i, minOf(i + 4, raw.length))
                            if (!unicode.matches(digits)) error("Illegal escape sequence: \\u$digits")
                            append(digits.toInt(16).toChar())
                            i += 4
                        }

                        else -> {
                            error("Illegal escape sequence: \"\\${escaped ?: ""}\"")
                        }
                    }
                }
            }
        return Token(Kind.STRING, value)
    }

    companion object {
        private val pairs = setOf(">=", "<=", "==", "!=", "||")
        private val unicode = Regex("[0-9a-fA-F]{4}")
    }
}

internal class Grammar(
    private val parser: Parser,
    source: String,
) {
    private val lexer = Lexer(parser, source)
    private var next = lexer.next()

    fun parse(): List<Instruction> =
        buildList {
            expression(this)
            expect(Kind.END, "EOF")
        }

    private fun consume() = next.also { next = lexer.next() }

    private fun accept(
        kind: Kind,
        name: String,
    ) = (next.kind == kind && next.value == name).also {
        if (it) consume()
    }

    private fun expect(
        kind: Kind,
        name: String,
    ) {
        if (!accept(kind, name)) lexer.error("Expected $name")
    }

    private fun acceptOp(name: String) = accept(Kind.OP, name)

    private fun prefix() = next.kind == Kind.OP && next.text in parser.unaryOps

    private fun expression(out: MutableList<Instruction>) {
        val current = mutableListOf<Instruction>()
        if (!accept(Kind.SEMICOLON, ";")) {
            assignment(current)
            if (!accept(Kind.SEMICOLON, ";")) {
                out.addAll(current)
                return
            }
        }
        if (next.kind != Kind.END && !(next.kind == Kind.PAREN && next.text == ")")) {
            current.add(Instruction(IENDSTATEMENT))
        }
        if (next.kind != Kind.END) expression(current)
        out.add(Instruction(IEXPR, current.toList()))
    }

    private fun assignment(out: MutableList<Instruction>) {
        conditional(out)
        while (acceptOp("=")) {
            val lhs = out.removeAt(out.lastIndex)
            when (lhs.type) {
                IFUNCALL -> {
                    if (!parser.isOperatorEnabled("()=")) throw IllegalArgumentException("function definition is not permitted")
                    val count = lhs.value as Int
                    for (index in out.size - count - 1 until out.size) {
                        if (index < 0) lexer.error("expected function name")
                        val instr = out[index]
                        if (instr.type == IVAR) out[index] = instr.copy(type = IVARNAME)
                    }
                    out.add(block(::assignment))
                    out.add(Instruction(IFUNDEF, count))
                }

                IVAR, IMEMBER -> {
                    out.add(lhs.copy(type = IVARNAME))
                    out.add(block(::assignment))
                    out.add(Instruction(IOP2, "="))
                }

                else -> {
                    throw IllegalArgumentException("expected variable for assignment")
                }
            }
        }
    }

    private fun block(parse: (MutableList<Instruction>) -> Unit) = Instruction(IEXPR, buildList(parse))

    private fun conditional(out: MutableList<Instruction>) {
        or(out)
        while (acceptOp("?")) {
            out.add(block(::conditional))
            expect(Kind.OP, ":")
            out.add(block(::conditional))
            out.add(Instruction(IOP3, "?"))
        }
    }

    private fun or(out: MutableList<Instruction>) {
        and(out)
        while (acceptOp("or")) {
            out.add(block(::and))
            out.add(Instruction(IOP2, "or"))
        }
    }

    private fun and(out: MutableList<Instruction>) {
        comparison(out)
        while (acceptOp("and")) {
            out.add(block(::comparison))
            out.add(Instruction(IOP2, "and"))
        }
    }

    private fun binary(
        out: MutableList<Instruction>,
        ops: Set<String>,
        parse: (MutableList<Instruction>) -> Unit,
    ) {
        parse(out)
        while (next.kind == Kind.OP && next.text in ops) {
            val op = consume().text
            parse(out)
            out.add(Instruction(IOP2, op))
        }
    }

    private fun comparison(out: MutableList<Instruction>) = binary(out, comparisons, ::additive)

    private fun additive(out: MutableList<Instruction>) = binary(out, additions, ::term)

    private fun term(out: MutableList<Instruction>) = binary(out, products, ::factor)

    private fun factor(out: MutableList<Instruction>) {
        if (prefix()) {
            val token = next
            val position = lexer.pos
            val op = consume().text
            if (op != "+" && op != "-") {
                if (next.kind == Kind.PAREN && next.text == "(") {
                    next = token
                    lexer.pos = position
                    power(out)
                    return
                }
                if (next.kind in bareUnaryEnd || (next.kind == Kind.PAREN && next.text == ")")) {
                    out.add(Instruction(IVAR, op))
                    return
                }
            }
            factor(out)
            out.add(Instruction(IOP1, op))
        } else {
            power(out)
        }
    }

    private fun power(out: MutableList<Instruction>) {
        call(out)
        while (acceptOp("!")) out.add(Instruction(IOP1, "!"))
        while (acceptOp("^")) {
            factor(out)
            out.add(Instruction(IOP2, "^"))
        }
    }

    private fun call(out: MutableList<Instruction>) {
        if (prefix()) {
            val op = consume().text
            atom(out)
            out.add(Instruction(IOP1, op))
            return
        }
        atom(out)
        while (true) {
            if (acceptOp(".")) {
                if (!parser.options.allowMemberAccess) throw IllegalArgumentException("unexpected \".\", member access is not permitted")
                if (next.kind != Kind.NAME) lexer.error("Expected TNAME")
                out.add(Instruction(IMEMBER, consume().value))
            } else if (accept(Kind.BRACKET, "[")) {
                expression(out)
                expect(Kind.BRACKET, "]")
                out.add(Instruction(IOP2, "["))
            } else {
                break
            }
        }
        while (accept(Kind.PAREN, "(")) out.add(Instruction(IFUNCALL, list(out, Kind.PAREN, ")")))
    }

    private fun atom(out: MutableList<Instruction>) {
        when {
            next.kind == Kind.NAME || prefix() -> {
                out.add(Instruction(IVAR, consume().value))
            }

            next.kind == Kind.LITERAL || next.kind == Kind.STRING -> {
                out.add(Instruction(INUMBER, consume().value))
            }

            accept(Kind.PAREN, "(") -> {
                expression(out)
                expect(Kind.PAREN, ")")
            }

            accept(Kind.BRACKET, "[") -> {
                out.add(Instruction(IARRAY, list(out, Kind.BRACKET, "]")))
            }

            else -> {
                throw IllegalArgumentException("unexpected $next")
            }
        }
    }

    private fun list(
        out: MutableList<Instruction>,
        kind: Kind,
        end: String,
    ): Int {
        var count = 0
        while (!accept(kind, end)) {
            do {
                expression(out)
                count++
            } while (accept(Kind.COMMA, ","))
        }
        return count
    }

    companion object {
        private val bareUnaryEnd = setOf(Kind.SEMICOLON, Kind.COMMA, Kind.END)
        private val comparisons = setOf("==", "!=", "<", "<=", ">=", ">", "in")
        private val additions = setOf("+", "-", "||")
        private val products = setOf("*", "/", "%")
    }
}
