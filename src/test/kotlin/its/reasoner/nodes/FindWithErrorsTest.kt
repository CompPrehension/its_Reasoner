package its.reasoner.nodes

import its.model.nodes.CycleAggregationNode
import its.model.nodes.FindActionNode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Поиск с категориями ошибок: категории перебираются по приоритету,
 * объект попадает только в первую подошедшую категорию.
 */
class FindWithErrorsTest : DecisionTreeTestBase() {

    private fun findNode(loqi: String) = tree(loqi).mainBranch.start as FindActionNode

    private fun cycleNode(loqi: String) = tree(loqi).mainBranch.start as CycleAggregationNode

    private fun categories(result: DecisionTreeReasoner.FindResult) = result.errors.entries.associate { (category, objects) -> category.priority to objects }

    /** Удачный поиск: найденный объект - правильный, остальные раскладываются по категориям ошибок. */
    @Test
    fun successfulFindClassifiesOtherObjects() {
        // Arrange.
        val node = findNode($$"""
            tpg T(X: item) {
                var Y: item error(
                    1: item -> $checked.sold,
                    2: item -> $checked.weight > 2
                ) = X->next;
                conclude: correct
            }
        """)
        val situation = situation("X" to "a")

        // Act.
        val result = DecisionTreeReasoner(situation).processWithErrors(node)

        // Assert.
        assertEquals(listOf(obj("b")), result.correct)
        assertEquals(mapOf(1 to listOf(obj("b"), obj("d")), 2 to listOf(obj("c"), obj("e"))), categories(result))
        assertEquals(obj("b"), situation.decisionTreeVariables["Y"])
    }

    /** Категории с меньшим приоритетом идут раньше и забирают объекты у последующих. */
    @Test
    fun categoriesAreOrderedByPriority() {
        // Arrange.
        val node = findNode($$"""
            tpg T(X: item) {
                var Y: item error(
                    5: item -> $checked.weight > 2,
                    1: item -> $checked.sold
                ) = X->next;
                conclude: correct
            }
        """)

        // Act.
        val result = DecisionTreeReasoner(situation("X" to "a")).processWithErrors(node)

        // Assert.
        assertEquals(listOf(1, 5), result.errors.keys.map { it.priority })
        assertEquals(mapOf(1 to listOf(obj("b"), obj("d")), 5 to listOf(obj("c"), obj("e"))), categories(result))
    }

    /** Неудачный поиск: правильных объектов нет, категории, ссылающиеся на искомую переменную, пропускаются. */
    @Test
    fun failedFindSkipsCategoriesUsingVariable() {
        // Arrange.
        val node = findNode($$"""
            tpg T(X: item) {
                var Y: item error(
                    1: item -> $checked=>leadsTo(Y),
                    2: item -> $checked.sold
                ) = find item i { X=>next($i) };
                conclude: correct
            }
        """)
        val situation = situation("X" to "e")

        // Act.
        val result = DecisionTreeReasoner(situation).processWithErrors(node)

        // Assert.
        assertEquals(emptyList(), result.correct)
        assertEquals(mapOf(2 to listOf(obj("b"), obj("d"))), categories(result))
        assertEquals(false, situation.decisionTreeVariables.containsKey("Y"))
    }

    /** Категория ошибок может ссылаться на найденную переменную. */
    @Test
    fun categoryMayUseFoundVariable() {
        // Arrange.
        val node = findNode($$"""
            tpg T(X: item) {
                var Y: item error(1: item -> $checked=>leadsTo(Y)) = X->next->next;
                conclude: correct
            }
        """)

        // Act.
        val result = DecisionTreeReasoner(situation("X" to "a")).processWithErrors(node)

        // Assert.
        assertEquals(listOf(obj("c")), result.correct)
        assertEquals(mapOf(1 to listOf(obj("a"), obj("b"))), categories(result))
    }

    /** Для цикла правильными считаются все объекты селектора. */
    @Test
    fun cycleSearchReturnsAllSelectedObjects() {
        // Arrange.
        val node = cycleNode($$"""
            tpg T(X: item) {
                cycle and ($i=>follows(X)) error(1: item -> $checked=>leadsTo(X), 2: item -> not $checked=>leadsTo()) with item i {
                    _ -> { conclude: correct };
                    correct -> { conclude: correct };
                }
            }
        """)

        // Act.
        val result = DecisionTreeReasoner(situation("X" to "b")).searchWithErrors(node)

        // Assert.
        assertEquals(listOf(obj("c"), obj("d")), result.correct)
        assertEquals(mapOf(1 to listOf(obj("a")), 2 to listOf(obj("d"), obj("e"))), categories(result))
    }

    /** Поиск без категорий даёт пустую карту ошибок. */
    @Test
    fun findWithoutCategoriesHasNoErrors() {
        // Arrange.
        val node = findNode("tpg T(X: item) { var Y: item = X->next; conclude: correct }")

        // Act.
        val result = DecisionTreeReasoner(situation("X" to "a")).processWithErrors(node)

        // Assert.
        assertEquals(listOf(obj("b")), result.correct)
        assertEquals(emptyMap(), result.errors)
    }
}
