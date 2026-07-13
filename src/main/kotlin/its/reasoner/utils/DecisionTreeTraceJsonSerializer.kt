package its.reasoner.utils

import its.model.definition.DomainModel
import its.model.definition.MetaData
import its.model.definition.loqi.OperatorLoqiWriter
import its.model.definition.types.Obj
import its.model.nodes.BranchResult
import its.model.nodes.DecisionTreeNode
import its.model.nodes.ThoughtBranch
import its.model.nodes.toView
import its.reasoner.nodes.AggregationDecisionTreeTraceElement
import its.reasoner.nodes.DecisionTreeTrace
import its.reasoner.nodes.DecisionTreeTraceElement
import its.reasoner.nodes.PartialDecisionTreeTrace
import its.reasoner.nodes.RedirectedBranchResultDecisionTreeTraceElement
import its.reasoner.nodes.WhileCycleDecisionTreeTraceElement
import its.reasoner.operators.ExpressionTrace

fun DecisionTreeTrace.toJsonValue(verbose: Boolean, domainModel: DomainModel): Map<String, Any> =
    mapOf(
        "branchResult" to branchResult.toString(),
        "finalVariables" to finalVariableSnapshot.toSortedMap()
            .mapValues { (_, value) -> value.toJsonValue(domainModel) },
        "elements" to this.map { element -> element.toJsonValue(verbose, domainModel) },
    )

fun PartialDecisionTreeTrace.toJsonValue(verbose: Boolean, domainModel: DomainModel): Map<String, Any?> =
    mapOf(
        "failedNode" to failedNode?.let { node ->
            linkedMapOf<String, Any?>(
                "nodeType" to node.javaClass.simpleName,
                "nodeId" to node.metadata.getString("id"),
                "line" to node.metadata.getString("line"),
                "metadata" to node.metadataJson(),
            ).also { result ->
                if (verbose) {
                    result["node"] = node.toString()
                }
            }
        },
        "variables" to variableSnapshot.toSortedMap().mapValues { (_, value) -> value.toJsonValue(domainModel) },
        "elements" to this.map { element -> element.toJsonValue(verbose, domainModel) },
    )

fun List<ExpressionTrace>.toJsonValue(verbose: Boolean, domainModel: DomainModel): List<Map<String, Any?>> =
    map { it.toJsonValue(verbose, domainModel) }

private fun ExpressionTrace.toJsonValue(verbose: Boolean, domainModel: DomainModel): Map<String, Any?> {
    val result = linkedMapOf<String, Any?>(
        "expression" to OperatorLoqiWriter.getWrittenExpression(expression),
        "value" to if (isValueAnnotated) value.toJsonCompatible(domainModel) else null,
        "isValueAnnotated" to isValueAnnotated,
        "children" to children.map { it.toJsonValue(verbose, domainModel) },
    )
    if (iterationObject != null) {
        result["iterationObject"] = iterationObject.toJsonCompatible(domainModel)
    }
    if (verbose) {
        result["expressionType"] = expression.javaClass.simpleName
    }
    return result
}

private fun DecisionTreeTraceElement<*, *>.toJsonValue(verbose: Boolean, domainModel: DomainModel): Map<String, Any?> {
    val result = linkedMapOf<String, Any?>(
        "nodeType" to node.javaClass.simpleName,
        "nodeId" to node.metadata.getString("id"),
        "nodeResult" to nodeResult.toJsonCompatible(domainModel),
        "variables" to variablesSnapshot.toSortedMap().mapValues { (_, value) -> value.toJsonValue(domainModel) },
        "metadata" to node.metadataJson(),
    )
    if (verbose) {
        result["node"] = node.toString()
    }

    when (this) {
        is AggregationDecisionTreeTraceElement<*> -> {
            result["aggregationMethod"] = node.aggregationMethod.toString()
            result["branches"] = branchTraceMap.entries.map { (branchInfo, nestedTrace) ->
                mapOf(
                    "branch" to branchInfo.toJsonCompatible(domainModel),
                    "trace" to nestedTrace.toJsonValue(verbose, domainModel),
                )
            }
        }

        is WhileCycleDecisionTreeTraceElement -> {
            result["iterations"] = branchTraceList.mapIndexed { index, nestedTrace ->
                mapOf(
                    "index" to index,
                    "trace" to nestedTrace.toJsonValue(verbose, domainModel),
                )
            }
        }

        is RedirectedBranchResultDecisionTreeTraceElement -> {
            result["redirectedTrace"] = subinterpreterTrace.toJsonValue(verbose, domainModel)
        }

        else -> Unit
    }

    return result
}

private fun Any?.toJsonCompatible(domainModel: DomainModel): Any? =
    when (this) {
        null -> null
        is Obj -> toJsonValue(domainModel)
        is Number, is Boolean, is String -> this
        is BranchResult -> toString()
        is ThoughtBranch -> metadataLabel(metadata, javaClass.simpleName)
        is DecisionTreeNode -> metadataLabel(metadata, javaClass.simpleName)
        is List<*> -> map { it.toJsonCompatible(domainModel) }
        else -> toString()
    }

private fun metadataLabel(metadata: MetaData, defaultLabel: String): String {
    val id = metadata.getString("id")
    return if (id != null) "$defaultLabel#$id" else defaultLabel
}

private fun DecisionTreeNode.metadataJson(): List<Map<String, Any?>> =
    toView().metadata.map { entry ->
        mapOf("name" to entry.name, "locCode" to entry.locCode, "value" to entry.value)
    }
