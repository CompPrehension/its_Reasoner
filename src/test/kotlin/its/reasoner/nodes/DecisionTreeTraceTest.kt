package its.reasoner.nodes

import its.model.nodes.BranchResult
import its.model.nodes.BranchResultNode
import its.model.nodes.Outcomes
import its.model.nodes.QuestionNode
import its.reasoner.ReasonerFixtures.expression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecisionTreeTraceTest : DecisionTreeTestBase() {

    private val aggregationTree = """
        tpg T(X: item) {
            ask (X.weight > 0) out true else { conclude: null };
            agg and {
                _ -> { conclude: correct };
                _ -> { ask (X.sold) { true -> { conclude: correct }; false -> { conclude: error } } };
                correct -> { conclude: correct };
                error -> { conclude: error with (X.sold = true) };
            }
        }
    """

    /** Результирующий элемент - последний, если его результат не повторяет предпоследний. */
    @Test
    fun resultingElementIsLastByDefault() {
        // Act.
        val solved = solve("tpg T(X: item) { ask (X.sold) out false else { conclude: error }; conclude: correct }", "X" to "a")

        // Assert.
        assertEquals(2, solved.trace.size)
        assertTrue(solved.trace.resultingNode is BranchResultNode)
        assertEquals(BranchResult.CORRECT, solved.result)
    }

    /** Узел агрегации остаётся результирующим, если следующий за ним вывод повторяет его результат. */
    @Test
    fun aggregationStaysResultingWhenFollowedBySameResult() {
        // Act.
        val solved = solve(aggregationTree, "X" to "a")

        // Assert.
        assertEquals(BranchResult.ERROR, solved.result)
        assertEquals(3, solved.trace.size)
        assertTrue(solved.trace.resultingElement is AggregationDecisionTreeTraceElement<*>)
        assertEquals(false, solved.trace.finalVariableSnapshot.isEmpty())
        assertEquals(true, solved.model.objects.get("a")!!.getPropertyValue("sold"))
    }

    /** Поиск узлов: по верхнему уровню и с учётом вложенных ветвей. */
    @Test
    fun nodeContainment() {
        // Act.
        val solved = solve(aggregationTree, "X" to "a")
        val nestedQuestion = solved.trace.first { it.isAggregated }.nestedTraces()!!.last().first().node

        // Assert.
        assertTrue(nestedQuestion is QuestionNode)
        assertTrue(solved.trace.containsNode(solved.trace.first().node))
        assertFalse(solved.trace.containsNode(nestedQuestion))
        assertTrue(solved.trace.containsWithNested(nestedQuestion))
        assertFalse(solved.trace.containsWithNested(QuestionNode(expression("true"), Outcomes(mutableListOf()))))
    }

    /** Трасса не может быть пустой и обязана оканчиваться результатом ветви. */
    @Test
    fun traceRequiresBranchResultAtEnd() {
        // Arrange.
        val solved = solve("tpg T(X: item) { ask (X.sold) out false else { conclude: error }; conclude: correct }", "X" to "a")

        // Act & Assert.
        assertFailsWith<IllegalArgumentException> { DecisionTreeTrace(emptyList()) }
        assertFailsWith<IllegalArgumentException> { DecisionTreeTrace(solved.trace.dropLast(1)) }
        assertEquals(1, DecisionTreeTrace(solved.trace.drop(1)).size)
    }

    /** Вывод с метаданными exception собирается как исключение ветви, в том числе из вложенных ветвей. */
    @Test
    fun branchResultExceptionsAreCollected() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                agg and {
                    _ -> { conclude: error [ exception = "true" ; exceptionName = "Nested" ; id = "n1" ; alias = "nested" ; label = "Nested error" ] };
                    _ -> { conclude: correct [ exception = "false" ; exceptionName = "Ignored" ] };
                    error -> { conclude: error [ exception = "1" ; exceptionName = "Outer" ; id = "o1" ] };
                }
            }
        """, "X" to "a")

        // Assert.
        assertEquals(
            listOf(
                BranchResultException(BranchResult.ERROR, "Nested", "n1", "nested", "Nested error"),
                BranchResultException(BranchResult.ERROR, "Outer", "o1"),
            ),
            solved.trace.branchResultExceptions()
        )
    }

    /** Исключение берётся из завершающего вывода ветви, даже если результат определила предшествующая агрегация. */
    @Test
    fun resultingExceptionComesFromFinalConclusion() {
        // Act.
        val direct = solve("tpg T(X: item) { conclude: error [ exception = \"true\" ; exceptionName = \"Direct\" ; id = \"d1\" ] }", "X" to "a")
        val afterAggregation = solve("""
            tpg T(X: item) {
                agg and {
                    _ -> { conclude: error };
                    error -> { conclude: error [ exception = "true" ; exceptionName = "Outer" ; id = "o1" ] };
                }
            }
        """, "X" to "a")

        // Assert.
        assertEquals(BranchResultException(BranchResult.ERROR, "Direct", "d1"), direct.trace.resultingBranchResultException())
        assertTrue(afterAggregation.trace.resultingElement is AggregationDecisionTreeTraceElement<*>)
        assertEquals(BranchResultException(BranchResult.ERROR, "Outer", "o1"), afterAggregation.trace.resultingBranchResultException())
    }

    /** Вывод без метаданных exception не является исключением. */
    @Test
    fun plainConclusionIsNotException() {
        // Act.
        val solved = solve("tpg T(X: item) { conclude: error [ exceptionName = \"NotAnException\" ] }", "X" to "a")

        // Assert.
        assertEquals(emptyList(), solved.trace.branchResultExceptions())
        assertNull(solved.trace.resultingBranchResultException())
    }

    /** Исключение без имени и идентификатора получает значения по умолчанию. */
    @Test
    fun exceptionDefaultsToUnknownNameAndId() {
        // Act.
        val solved = solve("tpg T(X: item) { conclude: error [ exception = \"true\" ] }", "X" to "a")

        // Assert.
        assertEquals(BranchResultException(BranchResult.ERROR, "unknown", "unknown"), solved.trace.resultingBranchResultException())
    }
}
