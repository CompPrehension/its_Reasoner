package its.reasoner.operators

import its.model.expressions.Operator

data class ExpressionTrace(
    val expression: Operator,
    val value: Any?,
    val isValueAnnotated: Boolean,
    val children: List<ExpressionTrace>,
    val iterationObject: Any? = null,
)

internal class ExpressionTraceState(
    val enabled: Boolean,
) {
    private val activeTraceNodes = mutableListOf<MutableExpressionTrace>()
    private val rootTraceNodes = mutableListOf<MutableExpressionTrace>()

    val trace: List<ExpressionTrace>
        get() = rootTraceNodes.map { it.toExpressionTrace() }

    fun add(traceNode: MutableExpressionTrace) {
        activeTraceNodes.lastOrNull()?.children?.add(traceNode) ?: rootTraceNodes.add(traceNode)
        activeTraceNodes.add(traceNode)
    }

    fun addIteration(expression: Operator, iterationObject: Any, result: Any?) {
        val siblings = activeTraceNodes.lastOrNull()?.children ?: rootTraceNodes
        if (result == false && siblings.count { it.iterationObject != null && it.value == false } >= 32) return
        siblings.add(MutableExpressionTrace(expression, result, true, mutableListOf(), iterationObject))
    }

    fun removeLastActive(traceNode: MutableExpressionTrace) {
        if (activeTraceNodes.lastOrNull() == traceNode) {
            activeTraceNodes.removeLast()
        }
    }
}

internal class MutableExpressionTrace(
    val expression: Operator,
    var value: Any? = null,
    var isValueAnnotated: Boolean = false,
    val children: MutableList<MutableExpressionTrace> = mutableListOf(),
    val iterationObject: Any? = null,
) {
    fun toExpressionTrace(): ExpressionTrace {
        return ExpressionTrace(
            expression = expression,
            value = value,
            isValueAnnotated = isValueAnnotated,
            children = children.map { it.toExpressionTrace() },
            iterationObject = iterationObject,
        )
    }
}
