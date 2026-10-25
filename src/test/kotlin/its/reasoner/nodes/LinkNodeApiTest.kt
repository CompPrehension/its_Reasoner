package its.reasoner.nodes

import its.model.nodes.BranchResult
import its.model.nodes.BranchResultNode
import its.model.nodes.FindActionNode
import its.model.nodes.QuestionNode
import its.reasoner.nodes.DecisionTreeReasoner.Companion.correctNext
import its.reasoner.nodes.DecisionTreeReasoner.Companion.execute
import its.reasoner.nodes.DecisionTreeReasoner.Companion.getAnswer
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Пошаговый интерфейс: ответ на отдельный узел, следующий узел и прорешивание отдельной ветви.
 */
class LinkNodeApiTest : DecisionTreeTestBase() {

    private val tree = tree("""
        tpg T(X: item) {
            var Y: item = X->next;
            ask (Y.sold) {
                true -> { conclude: correct };
                false -> { conclude: error };
            }
        }
    """)

    private val find get() = tree.mainBranch.start as FindActionNode

    private val question get() = find.outcomes[true]!!.node as QuestionNode

    /** Ответ на узел вычисляется с побочным эффектом на ситуацию. */
    @Test
    fun answerIsComputedForSingleNode() {
        // Arrange.
        val situation = situation("X" to "a")

        // Act.
        val found = find.getAnswer(situation)
        val sold = question.getAnswer(situation)

        // Assert.
        assertEquals(true, found)
        assertEquals(obj("b"), situation.decisionTreeVariables["Y"])
        assertEquals(true, sold)
    }

    /** Следующий узел выбирается по вычисленному ответу. */
    @Test
    fun correctNextFollowsAnswer() {
        // Arrange.
        val situation = situation("X" to "b")

        // Act.
        val afterFind = find.correctNext(situation)
        val afterQuestion = question.correctNext(situation)

        // Assert.
        assertTrue(afterFind is QuestionNode)
        assertEquals(BranchResult.ERROR, (afterQuestion as BranchResultNode).value)
    }

    /** Отсутствие исхода для ответа даёт null. */
    @Test
    fun correctNextWithoutOutcomeIsNull() {
        // Arrange.
        val node = tree("tpg T(X: item) { ask switch (X.color) { Color:red -> { conclude: correct } } }").mainBranch.start as QuestionNode

        // Act & Assert.
        assertTrue(node.correctNext(situation("X" to "a")) is BranchResultNode)
        assertNull(node.correctNext(situation("X" to "b")))
    }

    /** Выполнение узла даёт элемент трассы со снимком переменных. */
    @Test
    fun executeProducesTraceElement() {
        // Arrange.
        val situation = situation("X" to "a")

        // Act.
        val element = find.execute(situation)

        // Assert.
        assertTrue(element is LinkDecisionTreeTraceElement<*>)
        assertEquals(true, element.nodeResult)
        assertEquals(mapOf("X" to obj("a"), "Y" to obj("b")), element.variablesSnapshot)
    }

    /** Отдельная ветвь мысли прорешивается независимо от проверок дерева. */
    @Test
    fun branchIsSolvedIndependently() {
        // Arrange.
        val situation = situation("X" to "a")

        // Act.
        val trace = tree.mainBranch.solve(situation)

        // Assert.
        assertEquals(BranchResult.CORRECT, trace.branchResult)
        assertEquals(3, trace.size)
    }
}
