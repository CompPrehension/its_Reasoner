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

fun branchResultExceptionsEvent(trace: DecisionTreeTrace): Map<String, Any> {
    val exceptions = trace.branchResultExceptions()
    return mapOf(
        "type" to "exceptions",
        "found" to exceptions.isNotEmpty(),
        "value" to exceptions.map { exception ->
            mapOf(
                "result" to exception.result.toString(),
                "exceptionName" to exception.exceptionName,
                "id" to exception.nodeId,
            )
        },
    )
}

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
