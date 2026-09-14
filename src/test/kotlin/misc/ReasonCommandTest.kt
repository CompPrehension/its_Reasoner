package misc

import its.reasoner.ReasonerFixtures
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReasonCommandTest {

    @TempDir
    lateinit var root: Path

    private val model by lazy { CliFixture.modelDir(root).toString() }

    private fun situation(variable: String = "a") = CliFixture.situationFile(root, variable).toString()

    private fun reason(vararg args: String) = CliFixture.run("reason", model, situation(), *args)

    /** Успешное решение печатает результат, переменные и трассу; код выхода 0. */
    @Test
    fun humanOutputShowsTrace() {
        // Act.
        val run = reason()

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        val lines = run.stdout.lines()
        assertEquals("Result: CORRECT", lines[0])
        assertTrue(lines.contains("  Y = object b"))
        assertTrue(lines.any { it.startsWith("  1. FindActionNode") })
        assertTrue(lines.any { it.contains("BranchResultNode [id=ok]") })
        assertEquals("", run.stderr)
    }

    /** Без трассы печатается только сводка. */
    @Test
    fun noTracePrintsSummaryOnly() {
        // Act.
        val run = reason("--no-trace")

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(listOf("Result: CORRECT", "Variables:", "  X = object a", "  Y = object b"), run.stdout.trimEnd().lines())
    }

    /** Исключения ветвей выводятся отдельной сводкой. */
    @Test
    fun exceptionsSummaryIsPrinted() {
        // Act.
        val run = CliFixture.run("reason", model, situation("b"), "--no-trace")

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        assertTrue(run.stdout.contains("Exceptions:"))
        assertTrue(run.stdout.contains("id=notSold; result=ERROR; exceptionName=NotSold"))
    }

    /** JSONL: события результата, итогового узла, переменных, исключений, трассы и артефакта модели в фиксированном порядке. */
    @Test
    fun jsonlOutputEvents() {
        // Act.
        val run = reason("--format", "jsonl")
        val events = run.stdoutEvents

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(listOf("result", "final-node", "variables", "exceptions", "trace", "artifact"), events.map { it["type"] })
        assertEquals("CORRECT", events[0]["value"])
        assertEquals("BranchResultNode", events[1]["nodeType"])
        assertEquals("b", ((events[2]["value"] as Map<*, *>)["Y"] as Map<*, *>)["object_name"])
        assertEquals(false, events[3]["found"])
        assertTrue(events[4]["value"] is String)
        assertEquals("", run.stderr)
    }

    /** JSONL со структурной трассой и замером времени. */
    @Test
    fun jsonlStructuredTraceAndMetrics() {
        // Act.
        val run = reason("--format", "jsonl", "--json-trace", "--time-measure")
        val events = run.stdoutEvents

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        assertEquals("CORRECT", (events[4]["value"] as Map<*, *>)["branchResult"])
        assertEquals(listOf("preparationTime", "solveTime"), events.filter { it["type"] == "metric" }.map { it["name"] })
    }

    /** Дерево выбирается по имени файла tree_<имя>.loqi. */
    @Test
    fun namedTreeIsSelected() {
        // Act.
        val run = reason("--tree", "alt", "--no-trace")

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        assertEquals(listOf("Result: CORRECT", "Variables:", "  X = object a"), run.stdout.trimEnd().lines())
    }

    /** Отладочные процедуры допустимы только с --debug; их вывод в JSONL превращается в событие reasoner-output. */
    @Test
    fun debugProceduresRequireDebugFlag() {
        // Arrange.
        val debugModel = CliFixture.modelDir(root.resolve("debug"), withDebugTree = true).toString()

        // Act.
        val rejected = CliFixture.run("reason", debugModel, situation(), "--tree", "dbg")
        val human = CliFixture.run("reason", debugModel, situation(), "--tree", "dbg", "--debug", "--no-trace")
        val jsonl = CliFixture.run("reason", debugModel, situation(), "--tree", "dbg", "--debug", "--format", "jsonl")

        // Assert.
        assertEquals(1, rejected.exitCode)
        assertTrue(rejected.stderr.contains("debug-namespace procedures but debug=false"))
        assertEquals("debug tree", human.stdout.lines()[0])
        assertEquals(mapOf("type" to "reasoner-output", "level" to "debug", "value" to "debug tree"), jsonl.stdoutEvents.first())
    }

    /** Экспорт специфичной модели после решения: только объекты, с изменениями, без классов. */
    @Test
    fun exportDomainToFile() {
        // Arrange.
        val output = root.resolve("out.loqi")

        // Act.
        val run = reason("--tree", "alt", "-o", output.toString())
        val exported = ReasonerFixtures.domain(output.readText(), validate = false)

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        assertTrue(run.stdout.contains("Specific domain saved to"))
        assertEquals(0, exported.classes.size)
        assertEquals(true, exported.objects.get("a")!!.definedPropertyValues.get("sold", emptyMap())!!.value)
    }

    /** В JSONL экспорт в файл даёт событие service, а экспорт в "-" - артефакт с текстом LOQI. */
    @Test
    fun exportDomainInJsonl() {
        // Act.
        val toFile = reason("--format", "jsonl", "-o", root.resolve("out.loqi").toString())
        val inline = reason("--format", "jsonl", "-o", "-")

        // Assert.
        assertEquals(mapOf("type" to "service", "name" to "exportDomain", "status" to "success", "path" to root.resolve("out.loqi").toString()), toFile.stdoutEvents.last())
        val artifact = inline.stdoutEvents.last()
        assertEquals("artifact", artifact["type"])
        assertEquals("loqi", artifact["format"])
        assertTrue((artifact["value"] as String).contains("obj a"))
        assertFalse((artifact["value"] as String).contains("class item"))
    }

    /** Объекты тега считаются частью общей модели и не попадают в экспорт специфичной. */
    @Test
    fun tagObjectsAreNotExported() {
        // Act.
        val run = reason("--tag", "extra", "--format", "jsonl", "-o", "-")

        // Assert.
        assertEquals(0, run.exitCode, run.stderr)
        assertFalse((run.stdoutEvents.last()["value"] as String).contains("obj z"))
        assertTrue((run.stdoutEvents.last()["value"] as String).contains("obj a"))
    }

    /** Неизвестный тег - ошибка с перечислением известных. */
    @Test
    fun unknownTagFails() {
        // Act.
        val run = reason("--tag", "missing")

        // Assert.
        assertEquals(1, run.exitCode)
        assertTrue(run.stderr.contains("Tag 'missing' not found. Known tags: extra"))
        assertTrue(run.stderr.startsWith("reasoner-cli failed:"))
    }

    /** Некорректные опции отклоняются до запуска решения. */
    @Test
    fun invalidOptionsFail() {
        assertEquals(1, reason("--format", "xml").exitCode)
        assertEquals(1, reason("--time-limit", "0").exitCode)
        assertEquals(2, CliFixture.run("reason", model).exitCode)
    }

    /** Ошибка параметров в JSONL печатается событием error в поток ошибок. */
    @Test
    fun parameterErrorInJsonl() {
        // Act.
        val run = CliFixture.run("reason", "--format", "jsonl")

        // Assert.
        assertEquals(2, run.exitCode)
        assertEquals("", run.stdout)
        assertEquals("error", run.stderrEvents.single()["type"])
    }

    /** Ошибка решения: переменные на момент сбоя и событие error; с --debug добавляется частичная трасса. */
    @Test
    fun reasoningFailureInJsonl() {
        // Act.
        val plain = CliFixture.run("reason", model, situation("d"), "--format", "jsonl")
        val debug = CliFixture.run("reason", model, situation("d"), "--format", "jsonl", "--debug", "--json-trace")

        // Assert.
        assertEquals(1, plain.exitCode)
        assertEquals(listOf("variables"), plain.stdoutEvents.map { it["type"] })
        assertEquals("error", plain.stderrEvents.single()["type"])
        assertEquals(listOf("variables", "partial-expression-trace", "partial-trace"), debug.stdoutEvents.map { it["type"] })
        assertEquals("FindActionNode", ((debug.stdoutEvents[2]["value"] as Map<*, *>)["failedNode"] as Map<*, *>)["nodeType"])
    }

    /** Ошибка решения в человекочитаемом режиме: стек и переменные в потоке ошибок. */
    @Test
    fun reasoningFailureInHumanMode() {
        // Act.
        val run = CliFixture.run("reason", model, situation("d"), "--debug")

        // Assert.
        assertEquals(1, run.exitCode)
        assertTrue(run.stderr.contains("reasoner-cli failed:"))
        assertTrue(run.stderr.contains("  X = object d"))
        assertTrue(run.stderr.contains("Expression trace:"))
        assertTrue(run.stderr.contains("Partial decision tree trace:"))
    }

    /** Без подкоманды печатается справка. */
    @Test
    fun usageWithoutSubcommand() {
        // Act.
        val run = CliFixture.run()

        // Assert.
        assertEquals(0, run.exitCode)
        assertTrue(run.stdout.contains("Usage: reasoner-cli"), run.stdout)
        assertTrue(run.stdout.contains("reason"))
        assertTrue(run.stdout.contains("expression-query"))
    }
}
