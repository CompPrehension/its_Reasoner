package its.reasoner.utils

import java.io.PrintWriter
import java.io.StringWriter

fun printJsonLine(value: Map<String, Any?>) {
    println(toJson(value))
}

fun printJsonError(ex: Throwable) {
    System.err.println(
        toJson(
            mapOf(
                "type" to "error",
                "exceptionName" to ex.javaClass.name,
                "message" to ex.message,
                "stackTrace" to stackTraceToString(ex),
            )
        )
    )
}

fun toJson(value: Any?): String =
    when (value) {
        null -> "null"
        is String -> jsonString(value)
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, entryValue) ->
            "${jsonString(key.toString())}:${toJson(entryValue)}"
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { toJson(it) }
        else -> jsonString(value.toString())
    }

private fun stackTraceToString(ex: Throwable): String {
    val writer = StringWriter()
    ex.printStackTrace(PrintWriter(writer))
    return writer.toString()
}

private fun jsonString(value: String): String {
    val builder = StringBuilder(value.length + 2)
    builder.append('"')
    value.forEach { char ->
        when (char) {
            '"' -> builder.append("\\\"")
            '\\' -> builder.append("\\\\")
            '\b' -> builder.append("\\b")
            '\u000C' -> builder.append("\\f")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            else -> {
                if (char.code < 0x20) {
                    builder.append("\\u")
                    builder.append(char.code.toString(16).padStart(4, '0'))
                } else {
                    builder.append(char)
                }
            }
        }
    }
    builder.append('"')
    return builder.toString()
}
