package its.reasoner.nodes

import its.model.ValueTuple
import its.model.nodes.BranchResult.CORRECT
import its.model.nodes.BranchResult.ERROR
import its.model.nodes.BranchResult.NULL
import its.reasoner.ReasoningException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CycleAndTupleNodesTest : DecisionTreeTestBase() {

    /** Цикл while повторяет тело, пока условие истинно, и завершается с null, если тело не дало результата. */
    @Test
    fun whileCycleRunsUntilConditionFails() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                while (X=>next()) {
                    _ -> { conclude: null with (X = X->next) };
                    null -> { conclude: correct };
                }
            }
        """, "X" to "a")
        val element = solved.trace.first() as WhileCycleDecisionTreeTraceElement

        // Assert.
        assertEquals(CORRECT, solved.result)
        assertEquals(obj("d"), solved.variables["X"])
        assertEquals(3, element.branchTraceList.size)
        assertEquals(NULL, element.nodeResult)
    }

    /** Результат тела, отличный от null, прерывает цикл и становится результатом узла. */
    @Test
    fun whileCycleStopsAtBodyResult() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                while (X=>next()) {
                    _ -> {
                        ask (X->next.sold) out false else { conclude: error };
                        conclude: null with (X = X->next)
                    };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                }
            }
        """, "X" to "a")
        val element = solved.trace.first() as WhileCycleDecisionTreeTraceElement

        // Assert.
        assertEquals(ERROR, solved.result)
        assertEquals(obj("a"), solved.variables["X"])
        assertEquals(1, element.branchTraceList.size)
        assertEquals(ERROR, element.nodeResult)
    }

    /** Цикл с ложным условием не выполняет тело и даёт null. */
    @Test
    fun whileCycleWithFalseConditionIsSkipped() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                while (X=>next()) {
                    _ -> { conclude: error };
                    null -> { conclude: correct };
                }
            }
        """, "X" to "e")
        val element = solved.trace.first() as WhileCycleDecisionTreeTraceElement

        // Assert.
        assertEquals(CORRECT, solved.result)
        assertEquals(0, element.branchTraceList.size)
        assertEquals(emptyList(), element.nestedTraces()!!.toList())
    }

    /** Кортежный вопрос выбирает ветвь по совпадению всех частей. */
    @Test
    fun tupleQuestionMatchesAllParts() {
        // Arrange.
        val tree = """
            tpg T(X: item) {
                ask tuple (X.sold; X.color) {
                    (true; Color:green) -> { conclude: correct }
                    (false; Color:red) -> { conclude: error }
                    (false; Color:blue) -> { conclude: null }
                }
            }
        """

        // Act & Assert.
        assertEquals(CORRECT, solve(tree, "X" to "b").result)
        assertEquals(ERROR, solve(tree, "X" to "a").result)
        assertEquals(NULL, solve(tree, "X" to "c").result)
    }

    /** Кортеж без подходящей ветви - ошибка прорешивания. */
    @Test
    fun tupleWithoutMatchingBranchFails() {
        // Arrange.
        val tree = """
            tpg T(X: item) {
                ask tuple (X.sold; X.color) {
                    (true; Color:green) -> { conclude: correct }
                }
            }
        """

        // Act.
        val error = solveFails<IllegalArgumentException>(tree, "X" to "a")

        // Assert.
        assertTrue(error.message!!.contains("has no outcome with value"))
    }

    /** Ответ кортежного вопроса в трассе - совпавший кортеж исходов. */
    @Test
    fun tupleQuestionTraceHoldsMatchedTuple() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                ask tuple (X.sold; X.weight > 1) {
                    (true; true) -> { conclude: correct }
                    (false; true) -> { conclude: error }
                }
            }
        """, "X" to "c")

        // Assert.
        assertEquals(ERROR, solved.result)
        assertEquals(listOf(false, true), (solved.trace.first().nodeResult as ValueTuple).toList())
    }

    /** Вызов процедуры как шаг ветви выполняется и продолжает ветвь. */
    @Test
    fun procedureCallStatementContinuesBranch() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                debug:assert(X.weight == 1, "X must be a");
                eval(X.sold = true);
                conclude: correct
            }
        """, "X" to "a")

        // Assert.
        assertEquals(CORRECT, solved.result)
        assertEquals(3, solved.trace.size)
        assertEquals(true, solved.model.objects.get("a")!!.getPropertyValue("sold"))
    }

    /** Проваленная проверка в шаге ветви прерывает решение. */
    @Test
    fun failedAssertStatementStopsSolving() {
        solveFails<ReasoningException>("""
            tpg T(X: item) {
                debug:assert(X.weight == 2, "X must be b");
                conclude: correct
            }
        """, "X" to "a")
    }
}
