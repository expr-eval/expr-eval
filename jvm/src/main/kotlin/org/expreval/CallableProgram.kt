package org.expreval

import org.expreval.InstructionType.*

/**
 * Defer each operand so a callable evaluates it when its parent needs it.
 * This preserves left-to-right evaluation across nested statement blocks as
 * well as laziness across conditional and logical operators.
 */
internal fun callableProgram(tokens: List<Instruction>): List<Instruction> {
    val stack = mutableListOf<Instruction>()
    val statements = mutableListOf<Instruction>()

    fun pop() = stack.removeAt(stack.lastIndex)
    fun defer(item: Instruction) = if (item.type == IEXPR) item else Instruction(IEXPR, listOf(item))
    fun take(count: Int) = List(count) { pop() }.asReversed()
    fun branch(item: Instruction, operands: List<Instruction>) = Instruction(IEXPR, operands + item)
    fun name(item: Instruction): Instruction {
        require(item.type == IVARNAME) { "Expected a name in a callable assignment or function definition" }
        return item
    }

    for (item in tokens) {
        val prepared = when (item.type) {
            INUMBER, IVAR, IVARNAME, IEXPREVAL -> item
            IEXPR -> item.copy(value = callableProgram(item.block()))
            IOP1, IMEMBER -> branch(item, listOf(defer(pop())))
            IOP2 -> {
                val right = defer(pop())
                val left = pop()
                branch(item, listOf(if (item.value == "=") name(left) else defer(left), right))
            }
            IOP3 -> branch(item, take(3).map(::defer))
            IARRAY -> branch(item, take(item.value as Int).map(::defer))
            IFUNCALL -> branch(item, take((item.value as Int) + 1).map(::defer))
            IFUNDEF -> {
                val body = defer(pop())
                branch(item, take((item.value as Int) + 1).map(::name) + body)
            }
            IENDSTATEMENT -> {
                if (stack.isNotEmpty()) statements.add(pop())
                statements.add(item)
                continue
            }
        }
        stack.add(prepared)
    }
    return statements + stack
}
