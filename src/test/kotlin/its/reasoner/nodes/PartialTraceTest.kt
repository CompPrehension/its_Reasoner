package its.reasoner.nodes

import its.model.nodes.FindActionNode
import its.model.nodes.QuestionNode
import its.reasoner.AmbiguousObjectException
import its.reasoner.ReasoningException
import its.reasoner.ReasoningOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Частичная трасса: при ошибке решения сохраняются уже выполненные шаги, отказавший узел и переменные.
 */
class PartialTraceTest : DecisionTreeTestBase() {

    private val failingTree = """
        tpg T(X: item) {
            ask (X.weight > 0) out true else { conclude: null };
            var Y: item = X->next;
            ask (Y->next.sold) out true else { conclude: error };
            conclude: correct
        }
    """

    private val partial = ReasoningOptions(collectPartialTrace = true)

    /** Без запроса частичной трассы исключение ризонера её не несёт. */
    @Test
    fun partialTraceIsAbsentByDefault() {
        // Act.
        val error = assertFailsWith<AmbiguousObjectException> { solve(tree(failingTree), situation("X" to "c")) }

        // Assert.
        assertNull(error.partialDecisionTreeTrace)
    }

    /** Частичная трасса содержит выполненные шаги, отказавший узел и переменные на момент ошибки. */
    @Test
    fun partialTraceDescribesFailurePoint() {
        // Act.
        val error = assertFailsWith<ReasoningException> { solve(tree(failingTree), situation("X" to "c"), partial) }
        val trace = error.partialDecisionTreeTrace!!

        // Assert.
        assertEquals(listOf(QuestionNode::class, FindActionNode::class), trace.map { it.node::class })
        assertTrue(trace.failedNode is QuestionNode)
        assertEquals(mapOf("X" to obj("c"), "Y" to obj("d")), trace.variableSnapshot)
    }

    /** Не связанное с ризонером исключение оборачивается в исключение ризонера с частичной трассой. */
    @Test
    fun foreignExceptionIsWrappedWithPartialTrace() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.weight > 0) out true else { conclude: null };
                ask (1 and true) out true else { conclude: error };
                conclude: correct
            }
        """)

        // Act.
        val plain = assertFailsWith<Throwable> { solve(tree, situation("X" to "a")) }
        val wrapped = assertFailsWith<ReasoningException> { solve(tree, situation("X" to "a"), partial) }

        // Assert.
        assertTrue(plain is ClassCastException)
        assertTrue(wrapped.cause is ClassCastException)
        assertEquals(1, wrapped.partialDecisionTreeTrace!!.size)
    }

    /** Ошибка во вложенной ветви агрегации: частичная трасса описывает вложенную ветвь. */
    @Test
    fun nestedFailureKeepsInnermostPartialTrace() {
        // Arrange.
        val tree = tree("""
            tpg T(X: item) {
                ask (X.weight > 0) out true else { conclude: null };
                agg and {
                    _ -> {
                        var Y: item = X->next;
                        ask (Y->next->next->next.sold) out true else { conclude: error };
                        conclude: correct
                    };
                    error -> { conclude: error };
                }
            }
        """)

        // Act.
        val error = assertFailsWith<ReasoningException> { solve(tree, situation("X" to "a"), partial) }
        val trace = error.partialDecisionTreeTrace!!

        // Assert.
        assertEquals(listOf(FindActionNode::class), trace.map { it.node::class })
        assertTrue(trace.failedNode is QuestionNode)
        assertEquals(obj("b"), trace.variableSnapshot["Y"])
    }

    /** Ошибка при вычислении неявной переменной даёт частичную трассу без шагов. */
    @Test
    fun implicitVariableFailureGivesEmptyPartialTrace() {
        // Act.
        val error = assertFailsWith<ReasoningException> {
            solve(tree("tpg T(X: item, Y: item = X->next) { conclude: correct }"), situation("X" to "d"), partial)
        }

        // Assert.
        assertNotNull(error.partialDecisionTreeTrace)
        assertEquals(0, error.partialDecisionTreeTrace!!.size)
        assertEquals(mapOf("X" to obj("d")), error.partialDecisionTreeTrace!!.variableSnapshot)
    }
}
