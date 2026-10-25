package its.reasoner.utils

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonUtilsTest {

    /** Скалярные значения записываются по правилам JSON. */
    @Test
    fun scalarsAreSerialized() {
        assertEquals("null", toJson(null))
        assertEquals("true", toJson(true))
        assertEquals("42", toJson(42))
        assertEquals("2.5", toJson(2.5))
        assertEquals("\"text\"", toJson("text"))
    }

    /** Карты и списки записываются рекурсивно с сохранением порядка; элементы разделяются запятой с пробелом. */
    @Test
    fun mapsAndListsAreSerializedRecursively() {
        // Act.
        val json = toJson(linkedMapOf("a" to listOf(1, "x", null), "b" to mapOf("c" to false), "d" to emptyList<Any>()))

        // Assert.
        assertEquals("""{"a":[1, "x", null], "b":{"c":false}, "d":[]}""", json)
        assertEquals(mapOf("a" to listOf(1.0, "x", null), "b" to mapOf("c" to false), "d" to emptyList<Any>()), JsonParsing.parse(json))
    }

    /** Значения неизвестных типов записываются строкой через toString. */
    @Test
    fun otherValuesBecomeStrings() {
        assertEquals("\"RED\"", toJson(Color.RED))
        assertEquals("""{"1":"x"}""", toJson(mapOf(1 to "x")))
    }

    /** Спецсимволы в строках экранируются, кириллица остаётся как есть. */
    @Test
    fun stringsAreEscaped() {
        // Act.
        val json = toJson("кавычка \" слэш \\ \b\n\r\t  конец")

        // Assert.
        assertEquals("\"кавычка \\\" слэш \\\\ \\b\\f\\n\\r\\t \\u0001 конец\"", json)
        assertEquals("кавычка \" слэш \\ \b\n\r\t  конец", JsonParsing.parse(json))
    }

    /** Строка JSON пишется в стандартный вывод одной строкой. */
    @Test
    fun jsonLineIsPrintedToStdout() {
        // Act.
        val output = capture(System.out, System::setOut) { printJsonLine(mapOf("type" to "metric", "value" to 1)) }

        // Assert.
        assertEquals("""{"type":"metric", "value":1}""", output.trimEnd())
        assertEquals(1, output.trimEnd().lines().size)
    }

    /** Ошибка пишется в стандартный поток ошибок как событие error с именем класса, сообщением и стеком. */
    @Test
    fun errorIsPrintedToStderrAsEvent() {
        // Act.
        val output = capture(System.err, System::setErr) { printJsonError(IllegalStateException("boom")) }
        val event = JsonParsing.parse(output.trimEnd()) as Map<*, *>

        // Assert.
        assertEquals("error", event["type"])
        assertEquals("java.lang.IllegalStateException", event["exceptionName"])
        assertEquals("boom", event["message"])
        assertTrue((event["stackTrace"] as String).contains("JsonUtilsTest"))
    }

    private enum class Color { RED }

    private fun capture(original: PrintStream, set: (PrintStream) -> Unit, action: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        set(PrintStream(buffer, true, Charsets.UTF_8))
        try {
            action()
        } finally {
            set(original)
        }
        return buffer.toString(Charsets.UTF_8)
    }
}
