package its.reasoner.operators

import its.model.TypedVariable
import its.model.definition.loqi.OperatorLoqiBuilder
import its.model.definition.types.Obj
import its.reasoner.LearningSituation
import its.reasoner.operators.OperatorReasoner.Companion.evalAs
import its.reasoner.operators.TestModels.nodes
import its.reasoner.operators.TestModels.obj
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Кэш живёт только внутри вычисления выражения, не изменяющего состояние,
 * поэтому любое изменение модели - извне или самим выражением - видно всем последующим чтениям.
 */
class EvaluationCacheScopeTest {

    private fun chain() = nodes("""
        obj a : node { flag = false ; next(b) ; }
        obj b : node { flag = false ; next(c) ; }
        obj c : node { flag = false ; }
    """)

    /** Связь, удалённая извне между двумя вычислениями одного ризонера, больше не находится. */
    @Test
    fun externalLinkRemovalIsVisibleToNextEvaluation() {
        // Arrange.
        val model = chain()
        val reasoner = DomainInterpreterReasoner(LearningSituation(model))
        val leadsToC = OperatorLoqiBuilder.buildExp("obj:a=>leadsTo(obj:c)")
        assertEquals(true, leadsToC.evalAs(reasoner))

        // Act.
        val b = model.obj("b")
        b.relationshipLinks.remove(b.relationshipLinks.single())
        val result = leadsToC.evalAs<Boolean>(reasoner)

        // Assert.
        assertEquals(false, result)
    }

    /** Связь, добавленная извне между двумя вычислениями, находится и через обратное отношение. */
    @Test
    fun externalLinkAdditionIsVisibleToNextEvaluation() {
        // Arrange.
        val model = nodes("""
            obj a : node { flag = false ; }
            obj b : node { flag = false ; }
        """)
        val reasoner = DomainInterpreterReasoner(LearningSituation(model))
        val previous = OperatorLoqiBuilder.buildExp("obj:b->prev")
        assertEquals("error", runCatching { previous.evalAs<Obj>(reasoner) }.fold({ "result" }, { "error" }))

        // Act.
        val addLink = OperatorLoqiBuilder.buildExp("obj:a +=> next(obj:b)")
        addLink.evalAs<Any?>(reasoner)

        // Assert.
        assertEquals(Obj("a"), previous.evalAs(reasoner))
    }

    /** Свойство, изменённое извне между вычислениями, читается заново. */
    @Test
    fun externalPropertyChangeIsVisibleToNextEvaluation() {
        // Arrange.
        val model = chain()
        val reasoner = DomainInterpreterReasoner(LearningSituation(model))
        val anyFlag = OperatorLoqiBuilder.buildExp("forAny node n { \$n.flag }")
        assertEquals(false, anyFlag.evalAs(reasoner))

        // Act.
        OperatorLoqiBuilder.buildExp("obj:b.flag = true").evalAs<Any?>(DomainInterpreterReasoner(LearningSituation(model)))

        // Assert.
        assertEquals(true, anyFlag.evalAs(reasoner))
    }

    /** Изменение внутри выражения видно его же последующим итерациям. */
    @Test
    fun mutationInsideExpressionIsVisibleToLaterIterations() {
        // Arrange.
        val model = chain()
        val reasoner = DomainInterpreterReasoner(LearningSituation(model))
        val markFirstUnflagged = OperatorLoqiBuilder.buildExp("""
            forAll node n {
                (forAny node m { ${'$'}m.flag }) ? (${'$'}n.flag = false) : (${'$'}n.flag = true)
            }
        """)

        // Act.
        markFirstUnflagged.evalAs<Any?>(reasoner)

        // Assert.
        assertEquals(
            listOf(true, false, false),
            listOf("a", "b", "c").map { model.obj(it).getPropertyValue("flag", emptyMap()) }
        )
    }

    /** Отбор объектов по условию через публичный вход видит изменения между вызовами. */
    @Test
    fun objectSelectionSeesChangesBetweenCalls() {
        // Arrange.
        val model = chain()
        val reasoner = DomainInterpreterReasoner(LearningSituation(model))
        val followsA = OperatorLoqiBuilder.buildExp("\$n=>follows(obj:a)")
        val variable = TypedVariable("node", "n")
        assertEquals(listOf(Obj("b"), Obj("c")), reasoner.getObjectsByCondition(followsA, variable))

        // Act.
        val a = model.obj("a")
        a.relationshipLinks.remove(a.relationshipLinks.single())

        // Assert.
        assertEquals(emptyList(), reasoner.getObjectsByCondition(followsA, variable))
    }
}
