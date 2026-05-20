package its.reasoner.operators

import its.model.expressions.Operator

data class ExpressionTrace(
    val expression: Operator,
    val value: Any?,
    val isValueAnnotated: Boolean,
    val children: List<ExpressionTrace>,
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
) {
    fun toExpressionTrace(): ExpressionTrace {
        return ExpressionTrace(
            expression = expression,
            value = value,
            isValueAnnotated = isValueAnnotated,
            children = children.map { it.toExpressionTrace() },
        )
    }
}
