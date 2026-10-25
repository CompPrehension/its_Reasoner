package misc

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionQueryCommandTest {

    @TempDir
    lateinit var root: Path

    private val domain by lazy { CliFixture.standaloneDomainFile(root).toString() }

    private fun query(expression: String, vararg args: String) = CliFixture.run("expression-query", domain, expression, *args)

    /** Запрос-поиск печатает имена всех подходящих объектов. */
    @Test
    fun humanOutputListsObjects() {
        // Act.
        val run = query($$"find item i { $i.sold }")

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(listOf("Objects:", "  b", "  d"), run.stdout.trimEnd().lines())
    }

    /** Пустой результат помечается явно. */
    @Test
    fun emptyResultIsMarked() {
        // Act.
        val run = query($$"find item i { $i.weight > 100 }")

        // Assert.
        assertEquals(listOf("Objects:", "  <empty>"), run.stdout.trimEnd().lines())
    }

    /** Ограничение числа объектов и вывод их LOQI-описаний. */
    @Test
    fun limitAndLoqiOutput() {
        // Act.
        val run = query("find item i { true }", "--limit", "1", "--loqi")

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        val lines = run.stdout.trimEnd().lines()
        assertEquals("  a", lines[1])
        assertTrue(lines.any { it.startsWith("    obj a : item") })
        assertEquals(1, lines.count { it.startsWith("  ") && !it.startsWith("   ") })
    }

    /** Псевдоним подкоманды и трасса с замером времени. */
    @Test
    fun aliasWithTraceAndTiming() {
        // Act.
        val run = CliFixture.run("expr-query", domain, "X.weight > 0", "--trace", "--time-measure")

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        assertTrue(run.stdout.contains("Expression trace:"))
        assertTrue(run.stdout.contains("X.weight > 0 => true"))
        assertTrue(run.stdout.lines().any { it.startsWith("Query time:") })
    }

    /** JSONL: событие результата, затем трасса (текстовая или структурная) и метрика. */
    @Test
    fun jsonlEvents() {
        // Act.
        val text = query($$"find item i { $i.sold }", "--format", "jsonl", "--trace", "--time-measure")
        val structured = query($$"find item i { $i.sold }", "--format", "jsonl", "--trace", "--json-trace", "--loqi")

        // Assert.
        assertEquals(listOf("expression-query-result", "expression-trace", "metric"), text.stdoutEvents.map { it["type"] })
        assertEquals(listOf("b", "d"), text.stdoutEvents[0]["objects"])
        assertTrue(text.stdoutEvents[1]["value"] is String)
        assertTrue(structured.stdoutEvents[1]["value"] is List<*>)
        assertEquals(listOf("b", "d"), (structured.stdoutEvents[0]["objectsLoqi"] as List<*>).map { (it as Map<*, *>)["name"] })
    }

    /** Запрос с общей моделью и тегом: объекты тега видны запросу. */
    @Test
    fun queryWithModelDirAndTag() {
        // Arrange.
        val model = CliFixture.modelDir(root).toString()
        val situation = CliFixture.situationFile(root).toString()

        // Act.
        val withTag = CliFixture.run("expression-query", model, situation, $$"find item i { $i.weight > 5 }", "--tag", "extra")
        val withoutTag = CliFixture.run("expression-query", model, situation, $$"find item i { $i.weight > 5 }")

        // Assert.
        assertEquals(listOf("Objects:", "  z"), withTag.stdout.trimEnd().lines())
        assertEquals(listOf("Objects:", "  <empty>"), withoutTag.stdout.trimEnd().lines())
    }

    /** Тег без директории модели недопустим. */
    @Test
    fun tagRequiresModelDir() {
        // Act.
        val run = query("find item i { true }", "--tag", "extra")

        // Assert.
        assertEquals(1, run.exitCode)
        assertTrue(run.stderr.contains("--tag can be used only when MODEL_DIR is specified"))
    }

    /** Ошибка вычисления с --debug и --trace печатает частичную трассу выражения. */
    @Test
    fun failureWithDebugPrintsPartialExpressionTrace() {
        // Act.
        val run = query("obj:e->next.sold", "--debug", "--trace", "--format", "jsonl")

        // Assert.
        assertEquals(1, run.exitCode)
        assertEquals(listOf("partial-expression-trace"), run.stdoutEvents.map { it["type"] })
        assertEquals("error", run.stderrEvents.single()["type"])
    }

    /** Некорректные значения опций отклоняются. */
    @Test
    fun invalidOptionsFail() {
        assertEquals(1, query("find item i { true }", "--limit", "-1").exitCode)
        assertEquals(1, query("find item i { true }", "--format", "yaml").exitCode)
        assertEquals(2, CliFixture.run("expression-query", domain).exitCode)
    }
}
