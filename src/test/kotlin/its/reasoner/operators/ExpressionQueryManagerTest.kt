package its.reasoner.operators

import its.model.definition.types.Obj
import its.reasoner.ReasonerFixtures
import its.reasoner.ReasonerFixtures.expression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExpressionQueryManagerTest {

    private val manager = ExpressionQueryManager(ReasonerFixtures.situation(ReasonerFixtures.items(), "X" to "a"))

    /** Запрос-поиск возвращает все подходящие объекты, а не единственный. */
    @Test
    fun findQueryReturnsAllMatchingObjects() {
        // Act.
        val result = manager.query(expression($$"find item i { $i.sold }"))

        // Assert.
        assertEquals(listOf(Obj("b"), Obj("d")), result.objectRefs)
        assertEquals(listOf(Obj("b"), Obj("d")), result.value)
        assertEquals(emptyList(), result.trace)
    }

    /** Ограничение числа объектов действует только на список ссылок. */
    @Test
    fun limitTruncatesObjectRefsOnly() {
        // Act.
        val result = manager.query(expression("find item i { true }"), limit = 2)

        // Assert.
        assertEquals(listOf(Obj("a"), Obj("b")), result.objectRefs)
        assertEquals(5, (result.value as List<*>).size)
    }

    /** Отрицательное ограничение - ошибка. */
    @Test
    fun negativeLimitFails() {
        assertFailsWith<IllegalArgumentException> { manager.query(expression("find item i { true }"), limit = -1) }
    }

    /** Обычное выражение возвращает своё значение; ссылка на объект попадает в список ссылок. */
    @Test
    fun plainExpressionReturnsValue() {
        assertEquals(1, manager.query(expression("X.weight")).value)
        assertEquals(listOf(Obj("b")), manager.query(expression("X->next")).objectRefs)
        assertEquals(emptyList(), manager.query(expression("X.weight")).objectRefs)
    }

    /** По запросу собирается трасса выражения. */
    @Test
    fun traceIsCollectedOnRequest() {
        // Act.
        val result = manager.query(expression("X.weight > 0"), collectTrace = true)

        // Assert.
        assertEquals(true, result.value)
        assertTrue(result.trace.isNotEmpty())
    }
}
