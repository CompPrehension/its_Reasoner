package its.reasoner.utils

import its.model.definition.MetaData
import its.model.nodes.BranchResult
import its.model.nodes.DecisionTreeNode
import its.model.nodes.ThoughtBranch
import its.reasoner.nodes.AggregationDecisionTreeTraceElement
import its.reasoner.nodes.DecisionTreeTrace
import its.reasoner.nodes.DecisionTreeTraceElement
import its.reasoner.nodes.RedirectedBranchResultDecisionTreeTraceElement
import its.reasoner.nodes.WhileCycleDecisionTreeTraceElement

fun DecisionTreeTrace.toJsonValue(verbose: Boolean): Map<String, Any> =
    mapOf(
        "branchResult" to branchResult.toString(),
        "finalVariables" to finalVariableSnapshot.toSortedMap().mapValues { (_, value) -> value.toString() },
        "elements" to this.map { element -> element.toJsonValue(verbose) },
    )

private fun DecisionTreeTraceElement<*, *>.toJsonValue(verbose: Boolean): Map<String, Any?> {
    val result = linkedMapOf<String, Any?>(
        "nodeType" to node.javaClass.simpleName,
        "nodeId" to node.metadata.getString("id"),
        "nodeResult" to nodeResult.toJsonCompatible(),
        "variables" to variablesSnapshot.toSortedMap().mapValues { (_, value) -> value.toString() },
    )
    if (verbose) {
        result["node"] = node.toString()
    }

    when (this) {
        is AggregationDecisionTreeTraceElement<*> -> {
            result["branches"] = branchTraceMap.entries.map { (branchInfo, nestedTrace) ->
                mapOf(
                    "branch" to branchInfo.toJsonCompatible(),
                    "trace" to nestedTrace.toJsonValue(verbose),
                )
            }
        }

        is WhileCycleDecisionTreeTraceElement -> {
            result["iterations"] = branchTraceList.mapIndexed { index, nestedTrace ->
                mapOf(
                    "index" to index,
                    "trace" to nestedTrace.toJsonValue(verbose),
                )
            }
        }

        is RedirectedBranchResultDecisionTreeTraceElement -> {
            result["redirectedTrace"] = subinterpreterTrace.toJsonValue(verbose)
        }

        else -> Unit
    }

    return result
}

private fun Any?.toJsonCompatible(): Any? =
    when (this) {
        null -> null
        is Number, is Boolean, is String -> this
        is BranchResult -> toString()
        is ThoughtBranch -> metadataLabel(metadata, javaClass.simpleName)
        is DecisionTreeNode -> metadataLabel(metadata, javaClass.simpleName)
        else -> toString()
    }

private fun metadataLabel(metadata: MetaData, defaultLabel: String): String {
    val id = metadata.getString("id")
    return if (id != null) "$defaultLabel#$id" else defaultLabel
}
