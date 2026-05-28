package its.reasoner.operators

import its.model.definition.ObjectRef
import its.model.expressions.Operator
import its.model.expressions.operators.GetByCondition
import its.reasoner.LearningSituation

data class ExpressionQueryResult(
    val objectRefs: List<ObjectRef>,
    val value: Any?,
    val trace: List<ExpressionTrace>,
)

class ExpressionQueryManager(
    private val situation: LearningSituation,
) {
    fun query(
        expression: Operator,
        collectTrace: Boolean = false,
        limit: Int? = null,
    ): ExpressionQueryResult {
        require(limit == null || limit >= 0) { "Limit must be non-negative" }

        val reasoner = DomainInterpreterReasoner(
            situation = situation,
            collectExpressionTrace = collectTrace,
        )

        val value = when (expression) {
            is GetByCondition -> reasoner.getObjectsByCondition(expression.conditionExpr, expression.variable)
            else -> reasoner.evalWithTrace(expression)
        }

        return ExpressionQueryResult(
            objectRefs = extractObjectRefs(value).limitTo(limit),
            value = value,
            trace = reasoner.expressionTrace,
        )
    }

    private fun extractObjectRefs(value: Any?): List<ObjectRef> =
        when (value) {
            null -> emptyList()
            is ObjectRef -> listOf(value)
            is Iterable<*> -> value.filterIsInstance<ObjectRef>()
            is Array<*> -> value.filterIsInstance<ObjectRef>()
            else -> emptyList()
        }

    private fun List<ObjectRef>.limitTo(limit: Int?): List<ObjectRef> =
        if (limit == null) this else take(limit)
}
