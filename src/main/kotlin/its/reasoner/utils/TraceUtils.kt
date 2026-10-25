package its.reasoner.utils

import its.model.definition.MetaData
import its.model.definition.types.Obj
import its.model.expressions.Operator
import its.model.nodes.*
import its.reasoner.operators.ExpressionTrace
import its.reasoner.nodes.AggregationDecisionTreeTraceElement
import its.reasoner.nodes.DecisionTreeTrace
import its.reasoner.nodes.DecisionTreeTraceElement
import its.reasoner.nodes.PartialDecisionTreeTrace
import its.reasoner.nodes.RedirectedBranchResultDecisionTreeTraceElement
import its.reasoner.nodes.WhileCycleDecisionTreeTraceElement

fun formatDecisionTreeTrace(
    trace: DecisionTreeTrace,
    verbose: Boolean = false,
): String {
    val builder = StringBuilder()
    builder.appendLine("Result: ${trace.branchResult}")
    builder.appendLine("Variables:")
    appendVariables(builder, trace.finalVariableSnapshot, "  ")
    builder.appendLine("Trace:")
    appendTrace(builder, trace, verbose, "  ")
    return builder.toString().trimEnd()
}

fun formatExpressionTrace(
    trace: ExpressionTrace,
    verbose: Boolean = false,
): String {
    return formatExpressionTraces(listOf(trace), verbose)
}

fun formatPartialDecisionTreeTrace(
    trace: PartialDecisionTreeTrace,
    verbose: Boolean = false,
): String {
    val builder = StringBuilder()
    builder.appendLine("Partial decision tree trace:")
    trace.failedNode?.let { node ->
        builder.appendLine("Failed at: ${node.appendNodeMetadata(node.javaClass.simpleName)}")
    }
    builder.appendLine("Variables:")
    appendVariables(builder, trace.variableSnapshot, "  ")
    builder.appendLine("Trace:")
    appendTrace(builder, trace, verbose, "  ")
    return builder.toString().trimEnd()
}

fun formatExpressionTraces(
    traces: List<ExpressionTrace>,
    verbose: Boolean = false,
): String {
    val builder = StringBuilder()
    builder.appendLine("Expression trace:")
    appendExpressionTraces(builder, traces, verbose, "  ")
    return builder.toString().trimEnd()
}

private fun appendVariables(
    builder: StringBuilder,
    variables: Map<String, Obj>,
    indent: String,
) {
    if (variables.isEmpty()) {
        builder.appendLine("${indent}<empty>")
        return
    }

    variables.toSortedMap().forEach { (name, value) ->
        builder.appendLine("$indent$name = $value")
    }
}

private fun appendTrace(
    builder: StringBuilder,
    trace: Iterable<DecisionTreeTraceElement<*, *>>,
    verbose: Boolean,
    indent: String,
) {
    var isEmpty = true
    trace.forEachIndexed { index, element ->
        isEmpty = false
        appendTraceElement(builder, element, verbose, indent, index + 1)
    }
    if (isEmpty) {
        builder.appendLine("${indent}<empty>")
    }
}

private fun appendExpressionTraces(
    builder: StringBuilder,
    traces: List<ExpressionTrace>,
    verbose: Boolean,
    indent: String,
) {
    if (traces.isEmpty()) {
        builder.appendLine("${indent}<empty>")
        return
    }

    traces.forEachIndexed { index, trace ->
        appendExpressionTraceElement(builder, trace, verbose, indent, index + 1)
    }
}

private fun appendExpressionTraceElement(
    builder: StringBuilder,
    trace: ExpressionTrace,
    verbose: Boolean,
    indent: String,
    index: Int,
) {
    builder.append(indent)
    builder.append(index)
    builder.append(". ")
    builder.append(describeExpressionTraceElement(trace, verbose))
    builder.appendLine()

    val (iterationChildren, regularChildren) = trace.children.partition { it.iterationObject != null }
    regularChildren.forEachIndexed { childIndex, child ->
        appendExpressionTraceElement(builder, child, verbose, "$indent   ", childIndex + 1)
    }
    if (iterationChildren.isNotEmpty()) {
        appendIterationTraceElements(builder, iterationChildren, verbose, "$indent   ")
    }
}

private fun appendIterationTraceElements(
    builder: StringBuilder,
    iterationChildren: List<ExpressionTrace>,
    verbose: Boolean,
    indent: String,
) {
    val matched = iterationChildren.filter { it.value == true }
    val notMatched = iterationChildren.filter { it.value == false }
    val other = iterationChildren.filter { it.value != true && it.value != false }
    val hasDetailedFailure = iterationChildren.any { it.value != true && (it.children.isNotEmpty() || !it.isValueAnnotated) }

    builder.appendLine("$indent iterations: ${matched.size} matched / ${iterationChildren.size} checked")

    matched.forEach { child ->
        appendIterationTraceElement(builder, child, verbose, indent, '+')
    }

    other.forEach { child ->
        appendIterationTraceElement(builder, child, verbose, indent, '?')
    }

    if (notMatched.isNotEmpty()) {
        if (hasDetailedFailure) {
            notMatched.forEach { child ->
                appendIterationTraceElement(builder, child, verbose, indent, '-')
            }
        } else {
            builder.appendLine("$indent   - ${notMatched.size} not matched")
        }
    }
}

private fun appendIterationTraceElement(
    builder: StringBuilder,
    trace: ExpressionTrace,
    verbose: Boolean,
    indent: String,
    marker: Char,
) {
    builder.append(indent)
    builder.append("   ")
    builder.append(marker)
    builder.append(" ")
    builder.append(describeExpressionTraceElement(trace, verbose))
    builder.appendLine()

    trace.children.forEachIndexed { childIndex, child ->
        appendExpressionTraceElement(builder, child, verbose, "$indent      ", childIndex + 1)
    }
}

private fun appendTraceElement(
    builder: StringBuilder,
    element: DecisionTreeTraceElement<*, *>,
    verbose: Boolean,
    indent: String,
    index: Int,
) {
    builder.append(indent)
    builder.append(index)
    builder.append(". ")
    builder.appendLine(describeTraceElementHeadline(element, verbose))

    when (element) {
        is AggregationDecisionTreeTraceElement<*> -> {
            element.branchTraceMap.entries.forEachIndexed { nestedIndex, (branchInfo, nestedTrace) ->
                builder.appendLine("$indent   branch[$nestedIndex]: ${branchInfo.describeForTrace()}")
                appendTrace(builder, nestedTrace, verbose, "$indent      ")
            }
        }

        is WhileCycleDecisionTreeTraceElement -> {
            element.branchTraceList.forEachIndexed { iteration, nestedTrace ->
                builder.appendLine("$indent   iteration[$iteration]:")
                appendTrace(builder, nestedTrace, verbose, "$indent      ")
            }
        }

        is RedirectedBranchResultDecisionTreeTraceElement -> {
            builder.appendLine("$indent   redirected:")
            appendTrace(builder, element.subinterpreterTrace, verbose, "$indent      ")
        }

        else -> Unit
    }
}

private fun describeTraceElementHeadline(
    element: DecisionTreeTraceElement<*, *>,
    verbose: Boolean,
): String {
    val node = element.node
    val extras = ArrayList<String>(4)
    val id = node.metadata.getString("id")
    if (id != null) {
        extras += "id=$id"
    }
    node.metadata.getString("alias")?.let { extras += "alias=$it" }
    node.metadata.getString("label")?.let { extras += "label=$it" }
    node.metadata.getString("line")?.let { extras += "line=$it" }

    if (node is BranchResultNode) {
        node.metadata.getString("skill")?.let { extras += "skill=$it" }
        if (node.metadata.getString("exception")?.lowercase().equals("true")
            && node.metadata.containsAny("exceptionName")) {
            node.metadata.getString("exceptionName")?.let { extras += "exceptionName=$it" }
        } else if (node.metadata.containsAny("exception")) {
            node.metadata.getString("exception")?.let { extras += "exception=$it" }
        }
    }

    if (verbose && id == null) {
        extractSingleOperator(node)?.let { extras += "expr=${it.description.normalizeForTrace()}" }
    }

    val extrasString = if (extras.isEmpty()) "" else " [" + extras.joinToString(", ") + "]"
    return "${node.javaClass.simpleName}$extrasString => ${element.nodeResult.describeForTrace()}"
}

private fun extractSingleOperator(node: DecisionTreeNode): Operator? {
    return when (node) {
        is QuestionNode -> node.expr
        is CycleAggregationNode -> node.selectorExpr
        is WhileCycleNode -> node.conditionExpr
        is BranchResultNode -> node.actionExpr
        else -> null
    }
}

private fun describeExpressionTraceElement(
    trace: ExpressionTrace,
    verbose: Boolean,
): String {
    if (trace.iterationObject != null) {
        val label = "[${trace.iterationObject}]"
        return if (trace.isValueAnnotated) "$label => ${trace.value.describeForTrace()}" else label
    }

    val expression = if (verbose) {
        "${trace.expression.javaClass.simpleName}: ${trace.expression.description.normalizeForTrace()}"
    } else {
        trace.expression.description.normalizeForTrace()
    }

    return if (trace.isValueAnnotated) {
        "$expression => ${trace.value.describeForTrace()}"
    } else {
        expression
    }
}

private fun Any?.describeForTrace(): String {
    return when (this) {
        null -> "null"
        is ThoughtBranch -> metadataLabel(metadata, javaClass.simpleName)
        is DecisionTreeNode -> metadataLabel(metadata, javaClass.simpleName)
        else -> toString()
    }
}

private fun String.normalizeForTrace(): String {
    return lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
}

fun DecisionTreeNode.appendNodeMetadata(message: String): String {
    val extras = mutableListOf<String>()
    metadata.getString("id")?.trim()?.ifEmpty { null }?.let { extras += "id=$it" }
    metadata.getString("alias")?.trim()?.ifEmpty { null }?.let { extras += "alias=$it" }
    metadata.getString("label")?.trim()?.ifEmpty { null }?.let { extras += "label=$it" }
    metadata.getString("line")?.trim()?.ifEmpty { null }?.let { extras += "line=$it" }
    if (extras.isEmpty()) {
        return message
    }
    return "$message [${extras.joinToString(", ")}]"
}

private fun metadataLabel(metadata: MetaData, defaultLabel: String): String {
    val id = metadata.getString("id")
    val identity = if (id != null) "$defaultLabel#$id" else defaultLabel
    val extras = listOfNotNull(
        metadata.getString("alias")?.let { "alias=$it" },
        metadata.getString("label")?.let { "label=$it" },
    )
    return if (extras.isEmpty()) identity else "$identity [${extras.joinToString(", ")}]"
}
