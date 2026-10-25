package its.reasoner.utils

import its.model.definition.loqi.TreeLoqiWriter
import its.model.nodes.DecisionTree
import its.model.nodes.xml.DecisionTreeXMLWriter
import its.reasoner.LearningSituation
import its.reasoner.ReasonerFixtures
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `expression_tree.xml` и `expression_tree.loqi` - одно и то же дерево в двух форматах:
 * одинаковая структура при обратной записи и одинаковое поведение при прорешивании.
 */
class TreeFormatEquivalenceTest {

    private val fromXml: DecisionTree = ReasonerFixtures.expressionTree()
    private val fromLoqi: DecisionTree = ReasonerFixtures.tree(ReasonerFixtures.resourceText("expression_tree.loqi"))

    /** Оба дерева записываются в XML одинаково. */
    @Test
    fun treesAreEqualWhenWrittenAsXml() {
        assertEquals(DecisionTreeXMLWriter.decisionTreeToXmlString(fromXml), DecisionTreeXMLWriter.decisionTreeToXmlString(fromLoqi))
    }

    /** Оба дерева записываются в LOQI одинаково. */
    @Test
    fun treesAreEqualWhenWrittenAsLoqi() {
        assertEquals(TreeLoqiWriter.getWrittenTree(fromXml), TreeLoqiWriter.getWrittenTree(fromLoqi))
    }

    /** Прорешивание для каждого оператора выражения даёт одинаковую трассу и состояние модели. */
    @Test
    fun treesSolveIdenticallyForEveryOperator() {
        val model = ReasonerFixtures.expressionSituation()
        var solved = 0
        for (x in model.objects.filter { it.isInstanceOf("operator") }) {
            val expected = solve(fromXml, model.copy().let { LearningSituation(it, mutableMapOf("X" to x.reference)) })
            val actual = solve(fromLoqi, model.copy().let { LearningSituation(it, mutableMapOf("X" to x.reference)) })

            assertEquals(expected, actual, x.name)
            if (expected.startsWith("result:")) solved++
        }
        assertTrue(solved > 0)
    }

    private fun solve(tree: DecisionTree, situation: LearningSituation): String {
        val trace = runCatching { formatDecisionTreeTrace(tree.solve(situation), verbose = true) }
        return ReasonerFixtures.describe(trace) + System.lineSeparator() + ReasonerFixtures.dump(situation.domainModel)
    }
}
