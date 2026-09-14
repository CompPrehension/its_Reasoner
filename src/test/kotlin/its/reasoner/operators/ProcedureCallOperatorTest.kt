package its.reasoner.operators

import its.reasoner.ReasonerBreakpointException
import its.reasoner.ReasoningException
import its.reasoner.ReasoningMisuseException
import its.reasoner.TypingException
import its.reasoner.procedures.ReasonerOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcedureCallOperatorTest : OperatorTestBase() {

    override fun variables() = mapOf("X" to "a")

    private val output = mutableListOf<String>()

    private fun <T> capturing(action: () -> T): T = ReasonerOutput.withSink({ output.add(it) }, action)

    /** Успешная проверка возвращает true, а внутри блока - значение предыдущего выражения. */
    @Test
    fun assertReturnsPreviousBlockValue() {
        assertEquals(true, eval("debug:assert(true, \"ok\")"))
        assertEquals(5, eval("{ 5 ; debug:assert(true, \"ok\") }"))
    }

    /** Проваленная проверка - ошибка с переданным сообщением. */
    @Test
    fun failedAssertReportsMessage() {
        // Act.
        val error = evalFails<ReasoningException>("debug:assert(obj:a.sold, \"a must be sold\")")

        // Assert.
        assertTrue(error.message!!.contains("a must be sold"))
    }

    /** Аргументы процедуры проверяются по количеству и типу. */
    @Test
    fun procedureArgumentsAreTypeChecked() {
        evalFails<TypingException>("debug:assert(1, \"ok\")")
        evalFails<ReasoningMisuseException>("debug:assert(true)")
    }

    /** Печать выводит значение аргумента и возвращает значение предыдущего выражения. */
    @Test
    fun printWritesArgumentToOutput() {
        // Act.
        val result = capturing { evalOnce("{ obj:a.weight ; debug:print(obj:b.weight) }") }

        // Assert.
        assertEquals(1, result)
        assertEquals(listOf("2"), output)
    }

    /** Дамп выводит переменные дерева решений. */
    @Test
    fun dumpWritesDecisionTreeVariables() {
        // Act.
        capturing { evalOnce("debug:dump(\"here\")") }

        // Assert.
        assertEquals("<DEBUG>: here", output.first())
        assertTrue(output.any { it.startsWith("X:") && it.endsWith("a") })
    }

    /** Точка останова прерывает вычисление специальным исключением. */
    @Test
    fun breakpointInterruptsEvaluation() {
        // Act.
        val error = evalFails<ReasonerBreakpointException>("debug:breakpoint(\"stop here\")")

        // Assert.
        assertTrue(error.message!!.contains("stop here"))
    }

    /** Трассировка вычисляет выражение с трассой, печатает её и возвращает значение. */
    @Test
    fun traceEvaluatesAndPrintsExpression() {
        // Act.
        val result = capturing { evalOnce("debug:trace(obj:a.weight == 1)") }

        // Assert.
        assertEquals(true, result)
        assertEquals("---- expression trace (value: true) ----", output.first())
        assertEquals("---- end expression trace ----", output.last())
    }

    /** Трассировка видит переменные контекста места вызова. */
    @Test
    fun traceSeesScopeVariables() {
        // Act.
        val result = capturing { evalOnce($$"forAny item i [ $i.weight == 3 ] { debug:trace($i.sold) }") }

        // Assert.
        assertEquals(false, result)
        assertTrue(output.first().contains("value: false"))
    }

    /** Процедура eval выполняет выражение-аргумент и проверяет модель после него. */
    @Test
    fun evalExecutesArgumentAndValidatesModel() {
        // Act.
        eval("eval(obj:a.weight = 7)")

        // Assert.
        assertEquals(7, eval("obj:a.weight"))
        evalFails<Exception>("eval(obj:a.weight = \"heavy\")")
    }
}
