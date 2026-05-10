package its.reasoner.utils

import its.reasoner.nodes.DecisionTreeTrace

fun metricEvent(name: String, nanos: Long): Map<String, Any> =
    mapOf(
        "type" to "metric",
        "name" to name,
        "seconds" to nanos / 1_000_000_000.0,
        "milliseconds" to nanos / 1_000_000.0,
    )

fun resultEvent(trace: DecisionTreeTrace): Map<String, Any> =
    mapOf(
        "type" to "result",
        "name" to "branchResult",
        "value" to trace.branchResult.toString(),
    )

fun variablesEvent(trace: DecisionTreeTrace): Map<String, Any> =
    mapOf(
        "type" to "variables",
        "value" to trace.finalVariableSnapshot.toSortedMap().mapValues { (_, value) -> value.toString() },
    )

fun traceEvent(trace: DecisionTreeTrace, verbose: Boolean): Map<String, Any> =
    mapOf(
        "type" to "trace",
        "value" to trace.toJsonValue(verbose),
    )

fun reasonerOutputEvent(message: String): Map<String, Any> =
    mapOf(
        "type" to "reasoner-output",
        "level" to "debug",
        "value" to message,
    )
