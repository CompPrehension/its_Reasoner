package its.reasoner.operators

import its.model.definition.DomainModel
import its.model.definition.ParamsValues
import its.model.definition.RelationshipDef
import its.model.definition.RelationshipLinkStatement
import its.reasoner.ReasonerFixtures
import its.reasoner.ReasonerFixtures.combinations
import its.reasoner.ReasonerFixtures.describe
import its.reasoner.ReasonerFixtures.nodes
import its.reasoner.ReasonerFixtures.obj
import its.reasoner.ReasonerFixtures.relationship
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Поиск связей через кэш обязан давать ровно тот же результат, что и прямой перебор,
 * на любых сочетаниях объектов и на любой форме данных (в т.ч. не образующей корректной шкалы).
 */
class RelationshipCacheEquivalenceTest {

    private val expressionSituation = ReasonerFixtures.expressionSituation()

    /** На реальной модели выражения кэш и прямой перебор совпадают для всех сочетаний объектов. */
    @Test
    fun cachedLinksMatchUncachedOnExpressionModel() {
        val references = listOf(
            "token:directlyLeftOf", "token:directlyRightOf", "token:leftOf", "token:rightOf",
            "token:isBetween", "token:isCloserToThan", "token:isFurtherFromThan", "token:belongsTo",
            "operand:isOperandOf", "operand:hasOperand", "operand:isInOperandOf", "element:has",
        )
        for (reference in references) {
            val (className, relationshipName) = reference.split(":")
            assertSameLinksForAllCombinations(expressionSituation, expressionSituation.relationship(className, relationshipName))
        }
    }

    /** Несколько цепочек и одиночный объект: объекты разных цепочек не связаны. */
    @Test
    fun severalChainsAndIsolatedNode() {
        val model = nodes("""
            obj a : node { flag = false ; next(b) ; }
            obj b : node { flag = false ; next(c) ; }
            obj c : node { flag = false ; }
            obj d : node { flag = false ; next(e) ; }
            obj e : node { flag = false ; }
            obj f : node { flag = false ; }
        """)

        assertNotNull(ReadOnlyEvaluationCache(model).linearScale(model.relationship("node", "next")))
        assertSameLinksForAllRelationships(model)
    }

    /** Ветвление на линейной шкале, возникшее по ходу вывода: индекс не строится, ответы совпадают с перебором. */
    @Test
    fun branchingChainFallsBackToUncachedAlgorithm() {
        val model = nodes("""
            obj a : node { flag = false ; next(b) ; }
            obj b : node { flag = false ; }
            obj c : node { flag = false ; }
        """)
        val a = model.obj("a")
        a.relationshipLinks.add(RelationshipLinkStatement(a, "next", listOf("c"), ParamsValues.EMPTY))

        assertNull(ReadOnlyEvaluationCache(model).linearScale(model.relationship("node", "next")))
        assertSameLinksForAllRelationships(model)
    }

    /** Цикл на линейной шкале, возникший по ходу вывода: индекс не строится (сравнение с перебором невозможно - он на цикле зацикливается). */
    @Test
    fun cyclicChainDoesNotFormLinearScale() {
        val model = nodes("""
            obj a : node { flag = false ; next(b) ; }
            obj b : node { flag = false ; }
            obj c : node { flag = false ; }
        """)
        val b = model.obj("b")
        b.relationshipLinks.add(RelationshipLinkStatement(b, "next", listOf("a"), ParamsValues.EMPTY))

        assertNull(ReadOnlyEvaluationCache(model).linearScale(model.relationship("node", "next")))
    }

    private fun assertSameLinksForAllRelationships(model: DomainModel) {
        for (name in listOf("next", "prev", "leadsTo", "follows", "isBetween", "isCloser", "isFurther")) {
            assertSameLinksForAllCombinations(model, model.relationship("node", name))
        }
    }

    private fun assertSameLinksForAllCombinations(model: DomainModel, relationship: RelationshipDef) {
        val cache = ReadOnlyEvaluationCache(model)
        val subjects = model.objects.objectsAssignableTo(relationship.subjectClass.name)
        val objectLists = relationship.objectClasses.map { model.objects.objectsAssignableTo(it.name) }
        val objectCombinations = combinations(objectLists) + listOf(null)
        var checked = 0
        for (subj in subjects) {
            for (objects in objectCombinations) {
                val expected = runCatching { RelationshipUtils.findRelationshipLinks(subj, relationship, objects).map { describe(it, relationship) } }
                val actual = runCatching { RelationshipUtils.findRelationshipLinks(subj, relationship, objects, cache = cache).map { describe(it, relationship) } }
                assertEquals(
                    expected.exceptionOrNull()?.javaClass, actual.exceptionOrNull()?.javaClass,
                    "${relationship.name}: ${subj.name} -> ${objects?.map { it.name }}"
                )
                assertEquals(expected.getOrNull(), actual.getOrNull(), "${relationship.name}: ${subj.name} -> ${objects?.map { it.name }}")
                checked++
            }
        }
        assert(checked > 0)
    }
}
