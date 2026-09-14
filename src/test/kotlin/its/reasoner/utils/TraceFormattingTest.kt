package its.reasoner.utils

import its.reasoner.ReasonerFixtures
import its.reasoner.ReasonerFixtures.expression
import its.reasoner.ReasoningException
import its.reasoner.ReasoningOptions
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.operators.DomainInterpreterReasoner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Текстовое представление трасс: формат закреплён построчно, т.к. попадает в сообщения об ошибках и вывод CLI.
 */
class TraceFormattingTest {

    private fun situation(vararg variables: Pair<String, String>) = ReasonerFixtures.situation(ReasonerFixtures.items(), *variables)

    private fun lines(text: String) = text.lines()

    /** Трасса дерева: результат, переменные и пронумерованные шаги с вложенными ветвями. */
    @Test
    fun decisionTreeTraceFormat() {
        // Arrange.
        val tree = ReasonerFixtures.tree("""
            tpg T(X: item) {
                var Y: item = X->next;
                agg and {
                    _ -> { ask (Y.sold) out true else { conclude: error }; conclude: correct };
                    correct -> { conclude: correct [ id = "done" ; alias = "fin" ; skill = "s1" ] };
                }
            }
        """)

        // Act.
        val trace = tree.solve(situation("X" to "a"))

        // Assert.
        assertEquals(
            listOf(
                "Result: CORRECT",
                "Variables:",
                "  X = object a",
                "  Y = object b",
                "Trace:",
                "  1. FindActionNode => true",
                "  2. BranchAggregationNode => CORRECT",
                "     branch[0]: ThoughtBranch",
                "        1. QuestionNode => true",
                "        2. BranchResultNode => CORRECT",
                "  3. BranchResultNode [id=done, alias=fin, skill=s1] => CORRECT",
            ),
            lines(formatDecisionTreeTrace(trace))
        )
    }

    /** Подробный режим добавляет выражения узлов без идентификаторов. */
    @Test
    fun verboseTraceShowsExpressions() {
        // Arrange.
        val tree = ReasonerFixtures.tree("tpg T(X: item) { ask (X.sold) out false else { conclude: error }; conclude: correct with (X.sold = true) }")

        // Act.
        val trace = tree.solve(situation("X" to "a"))

        // Assert.
        assertEquals(
            listOf(
                "  1. QuestionNode [expr=X.sold] => false",
                "  2. BranchResultNode [expr=X.sold = true] => CORRECT",
            ),
            lines(formatDecisionTreeTrace(trace, verbose = true)).drop(4)
        )
    }

    /** Частичная трасса: отказавший узел и выполненные шаги. */
    @Test
    fun partialTraceFormat() {
        // Arrange.
        val tree = ReasonerFixtures.tree("""
            tpg T(X: item) {
                ask (X.weight > 0) out true else { conclude: null };
                ask (X->next.sold) out true else { conclude: error } as q2;
                conclude: correct
            }
            meta for q2 [ id = "q2" ]
        """)

        // Act.
        val error = assertFailsWith<ReasoningException> { tree.solve(situation("X" to "d"), ReasoningOptions(collectPartialTrace = true)) }

        // Assert.
        assertEquals(
            listOf(
                "Partial decision tree trace:",
                "Failed at: QuestionNode [id=q2]",
                "Variables:",
                "  X = object d",
                "Trace:",
                "  1. QuestionNode => true",
            ),
            lines(formatPartialDecisionTreeTrace(error.partialDecisionTreeTrace!!))
        )
    }

    /** Трасса выражения: вложенность, значения и сводка по итерациям селектора. */
    @Test
    fun expressionTraceFormat() {
        // Arrange.
        val reasoner = DomainInterpreterReasoner(situation("X" to "a"), collectExpressionTrace = true)

        // Act.
        reasoner.evalWithTrace(expression($$"forAny item i [ $i.sold ] { $i == X->next }"))

        // Assert.
        assertEquals(
            listOf(
                "Expression trace:",
                "  1. forAny item i [\$i.sold] {\$i == X->next} => true",
                "     1. \$i == X->next => true",
                "        1. \$i => object b",
                "        2. X->next => object b",
                "           1. X => object a",
                "      iterations: 2 matched / 5 checked",
                "        + [object b] => true",
                "           1. \$i => object b",
                "        + [object d] => true",
                "           1. \$i => object d",
                "        - 3 not matched",
            ),
            lines(formatExpressionTraces(reasoner.expressionTrace))
        )
    }

    /** Сообщение об ошибке дополняется метаданными узла. */
    @Test
    fun nodeMetadataIsAppendedToMessages() {
        // Arrange.
        val tree = ReasonerFixtures.tree("""
            tpg T(X: item) { ask (X.sold) out true else { conclude: error } as q; conclude: correct }
            meta for q [ id = "q1" ; alias = "sold?" ; label = "Is it sold" ]
        """)

        // Act & Assert.
        assertEquals("boom [id=q1, alias=sold?, label=Is it sold]", tree.mainBranch.start.appendNodeMetadata("boom"))
        assertEquals("boom", ReasonerFixtures.tree("tpg T(X: item) { conclude: correct }").mainBranch.start.appendNodeMetadata("boom"))
    }
}
