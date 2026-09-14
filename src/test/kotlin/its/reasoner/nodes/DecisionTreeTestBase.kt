package its.reasoner.nodes

import its.model.definition.DomainModel
import its.model.definition.types.Obj
import its.model.nodes.DecisionTree
import its.reasoner.LearningSituation
import its.reasoner.ReasonerFixtures
import its.reasoner.ReasonerFixtures.copy
import its.reasoner.ReasonerFixtures.describe
import its.reasoner.ReasonerFixtures.dump
import its.reasoner.ReasoningOptions
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.utils.formatDecisionTreeTrace
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Каждое дерево прорешивается дважды - с кэшем производных данных и без него -
 * на независимых копиях ситуации; трассы, исключения и итоговое состояние обязаны совпадать.
 */
abstract class DecisionTreeTestBase {

    protected class Solved(val trace: DecisionTreeTrace, val situation: LearningSituation) {
        val result get() = trace.branchResult
        val variables get() = situation.decisionTreeVariables
        val model get() = situation.domainModel
    }

    protected open fun domain(): DomainModel = ReasonerFixtures.items()

    protected fun tree(loqi: String): DecisionTree = ReasonerFixtures.tree(loqi)

    protected fun situation(vararg variables: Pair<String, String>): LearningSituation {
        return ReasonerFixtures.situation(domain(), *variables)
    }

    protected fun solve(treeLoqi: String, vararg variables: Pair<String, String>): Solved {
        return solve(tree(treeLoqi), situation(*variables))
    }

    protected fun solve(tree: DecisionTree, situation: LearningSituation, options: ReasoningOptions = ReasoningOptions.DEFAULT): Solved {
        val reference = situation.copy()
        val expected = runCatching { tree.solve(reference, options.copy(useEvaluationCache = false)) }
        val actual = runCatching { tree.solve(situation, options.copy(useEvaluationCache = true)) }

        assertEquals(describe(expected.map { format(it) }), describe(actual.map { format(it) }), "трасса без кэша и с кэшем")
        assertEquals(dump(reference.domainModel), dump(situation.domainModel), "состояние модели без кэша и с кэшем")
        assertEquals(reference.decisionTreeVariables, situation.decisionTreeVariables, "переменные дерева без кэша и с кэшем")
        return Solved(actual.getOrThrow(), situation)
    }

    protected inline fun <reified E : Throwable> solveFails(treeLoqi: String, vararg variables: Pair<String, String>): E {
        return assertFailsWith<E> { solve(treeLoqi, *variables) }
    }

    protected fun obj(name: String) = Obj(name)

    protected fun format(trace: DecisionTreeTrace): String = formatDecisionTreeTrace(trace, verbose = true)
}
