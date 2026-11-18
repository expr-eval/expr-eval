package org.expreval

/** The shared Function.prototype.bind property. Reading it must not implicitly bind its receiver. */
internal object FunctionBind : ExpressionFunction {
    override fun call(vararg args: Any?): Any? =
        throw IllegalArgumentException("Bind must be called on a function")

    override fun callWithReceiver(receiver: Any?, vararg args: Any?): Any? {
        require(receiver is ExpressionFunction) { "Bind must be called on a function" }
        return BoundFunction(receiver, args.arg(0), args.drop(1))
    }
}

/** Each bind creates a distinct callable; rebinding retains the original receiver and argument order. */
internal class BoundFunction(
    val target: ExpressionFunction,
    private val receiver: Any?,
    arguments: List<Any?>,
) : ExpressionFunction {
    private val arguments = arguments.toList()

    override fun call(vararg args: Any?): Any? =
        target.callWithReceiver(receiver, *(arguments + args).toTypedArray())
}
