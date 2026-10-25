package its.reasoner.operators

import its.model.definition.DomainModel
import its.model.definition.loqi.DomainLoqiWriter
import its.model.definition.loqi.OperatorLoqiBuilder
import its.model.definition.types.Obj
import its.model.expressions.Operator
import its.reasoner.LearningSituation
import its.reasoner.ReasonerFixtures
import its.reasoner.ReasoningOptions
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.operators.OperatorReasoner.Companion.evalAs
import its.reasoner.utils.formatDecisionTreeTrace
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Вычисление с кэшем обязано быть неотличимо от вычисления без него:
 * те же результаты, те же исключения, то же итоговое состояние модели.
 */
class EvaluationCacheEquivalenceTest {

    private val model = ReasonerFixtures.expressionSituation()
    private val tree = ReasonerFixtures.expressionTree()

    private val findLeftBlocker = OperatorLoqiBuilder.buildExp("""
        find operator Y {
            forAny token y_fin [
                ${'$'}y_fin == findExtreme y1_ex [${'$'}y1_ex=>isBetween(${'$'}y1, X)] among token y1 {
                    ${'$'}y1=>leftOf(X)
                    and (${'$'}y1->belongsTo is class:operator and ${'$'}y1->belongsTo.state == state:unevaluated) and not (
                        forAny separator commaSep [${'$'}commaSep=>isBetween(${'$'}y1, X)] {
                            not (
                                forAny token f1 [${'$'}f1->belongsTo is class:operator] {
                                    forAny token f2 [${'$'}f2->belongsTo == ${'$'}f1->belongsTo] {
                                        ${'$'}commaSep=>isBetween(${'$'}f1, ${'$'}f2) and ${'$'}f1=>isBetween(${'$'}y1, X) and ${'$'}f2=>isBetween(${'$'}y1, X)
                                    }
                                }
                            )
                        }
                    )
                }
            ] {
                ${'$'}Y=>has(${'$'}y_fin)
            }
        }
    """)

    private fun operators() = model.objects.filter { it.isInstanceOf("operator") }

    /** Поиск мешающего оператора слева даёт один и тот же ответ для каждого X. */
    @Test
    fun searchExpressionMatchesUncachedForEveryOperator() {
        for (x in operators()) {
            val expected = evaluate(findLeftBlocker, x.reference, useCache = false)
            val actual = evaluate(findLeftBlocker, x.reference, useCache = true)

            assertEquals(expected, actual, x.name)
        }
    }

    /** Полное прорешивание дерева даёт ту же трассу и то же состояние модели для каждого X. */
    @Test
    fun decisionTreeSolvingMatchesUncachedForEveryOperator() {
        var solved = 0
        for (x in operators()) {
            val expected = solveTree(x.reference, useCache = false)
            val actual = solveTree(x.reference, useCache = true)

            assertEquals(expected, actual, x.name)
            if (expected.startsWith("result:")) solved++
        }
        assertTrue(solved > 0)
    }

    private fun evaluate(expression: Operator, x: Obj, useCache: Boolean): String {
        val situation = LearningSituation(model.copy(), mutableMapOf("X" to x))
        val reasoner = DomainInterpreterReasoner(situation, useEvaluationCache = useCache)
        return outcome { expression.evalAs<Any?>(reasoner) }
    }

    private fun solveTree(x: Obj, useCache: Boolean): String {
        val domain = model.copy()
        val situation = LearningSituation(domain, mutableMapOf("X" to x))
        val trace = outcome { formatDecisionTreeTrace(tree.solve(situation, ReasoningOptions(useEvaluationCache = useCache)), verbose = true) }
        return trace + System.lineSeparator() + loqi(domain)
    }

    private fun outcome(action: () -> Any?): String {
        return runCatching(action).fold({ "result: $it" }, { "error: ${it.javaClass.name}: ${it.message}" })
    }

    private fun loqi(domain: DomainModel): String {
        return StringWriter().also { DomainLoqiWriter.saveDomain(domain, it) }.toString()
    }
}
