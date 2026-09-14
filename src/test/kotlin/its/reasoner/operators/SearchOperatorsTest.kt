package its.reasoner.operators

import its.model.TypedVariable
import its.reasoner.AmbiguousObjectException
import its.reasoner.ReasonerFixtures.expression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchOperatorsTest : OperatorTestBase() {

    override fun variables() = mapOf("X" to "c")

    /** Поиск возвращает единственный подходящий объект. */
    @Test
    fun findReturnsSingleMatch() {
        assertEquals(obj("e"), eval($$"find item i { $i.weight == 5 }"))
        assertEquals(obj("b"), eval($$"find glass g { $g.color == Color:green }"))
        assertEquals(obj("box2"), eval($$"find box b { $b=>holds(obj:d) }"))
    }

    /** Поиск без подходящих объектов возвращает null. */
    @Test
    fun findWithoutMatchReturnsNull() {
        assertNull(eval($$"find item i { $i.weight > 100 }"))
        assertNull(eval($$"find glass g { $g.color == Color:blue }"))
    }

    /** Поиск при нескольких подходящих объектах - ошибка. */
    @Test
    fun findWithSeveralMatchesFails() {
        // Act.
        val error = evalFails<AmbiguousObjectException>($$"find item i { $i.sold }")

        // Assert.
        assertEquals(true, error.message!!.contains("2 fitting objects"))
    }

    /** Поиск учитывает переменные дерева и контекста. */
    @Test
    fun findUsesOuterVariables() {
        assertEquals(obj("d"), eval($$"find item i { X=>next($i) }"))
        assertEquals(obj("b"), eval($$"find item i { $i=>next($n) }", mapOf("n" to obj("c"))))
    }

    /** Поиск вида "$x == выражение" возвращает значение выражения, не перебирая объекты. */
    @Test
    fun findByEqualityReturnsExpressionValue() {
        assertEquals(obj("b"), eval($$"find item i { $i == obj:a->next }"))
        assertEquals(obj("b"), eval($$"find item i { obj:a->next == $i }"))
        assertNull(eval($$"find item i { $i == (find item j { false }) }"))
    }

    /** Поиск вида "$x == выражение" не проверяет класс найденного объекта. */
    @Test
    fun findByEqualityDoesNotCheckClass() {
        assertEquals(obj("box1"), eval($$"find glass g { $g == obj:box1 }"))
    }

    /** Поиск экстремума возвращает объект, "экстремальнее" всех остальных отобранных. */
    @Test
    fun findExtremeReturnsSingleExtreme() {
        assertEquals(obj("e"), eval($$"findExtreme heaviest [ $heaviest.weight > $i.weight ] among item i { true }"))
        assertEquals(obj("a"), eval($$"findExtreme first [ $first=>leadsTo($i) ] among item i { $i=>leadsTo() or $i=>follows() }"))
        assertEquals(obj("b"), eval($$"findExtreme lightest [ $lightest.weight < $g.weight ] among glass g { true }"))
    }

    /** Поиск экстремума без отобранных объектов возвращает null. */
    @Test
    fun findExtremeWithoutCandidatesReturnsNull() {
        assertNull(eval($$"findExtreme heaviest [ $heaviest.weight > $i.weight ] among item i { false }"))
    }

    /** Поиск экстремума, где ни один объект не экстремальнее остальных, возвращает null. */
    @Test
    fun findExtremeWithoutExtremeReturnsNull() {
        assertNull(eval($$"findExtreme first [ $first=>leadsTo($i) ] among item i { true }"))
    }

    /** Единственный отобранный объект экстремален сам по себе. */
    @Test
    fun singleCandidateIsExtreme() {
        assertEquals(obj("e"), eval($$"findExtreme first [ false ] among item i { $i.weight == 5 }"))
    }

    /** Несколько экстремальных объектов - ошибка. */
    @Test
    fun findExtremeWithSeveralExtremesFails() {
        evalFails<AmbiguousObjectException>($$"findExtreme any [ true ] among item i { true }")
    }

    /** Отбор объектов по условию через публичный вход возвращает все подходящие объекты в порядке модели. */
    @Test
    fun objectsByConditionReturnsAllMatches() {
        // Arrange.
        val reasoner = DomainInterpreterReasoner(situation)

        // Act.
        val sold = reasoner.getObjectsByCondition(expression($$"$i.sold"), TypedVariable("item", "i"))
        val all = reasoner.getObjectsByCondition(null, TypedVariable("glass", "i"))

        // Assert.
        assertEquals(objects("b", "d"), sold)
        assertEquals(objects("b", "d"), all)
    }

    /** Отбор объектов по условию через публичный вход учитывает переменные дерева. */
    @Test
    fun objectsByConditionUsesDecisionTreeVariables() {
        // Arrange.
        val reasoner = DomainInterpreterReasoner(situation)

        // Act.
        val following = reasoner.getObjectsByCondition(expression($$"$i=>follows(X)"), TypedVariable("item", "i"))

        // Assert.
        assertEquals(objects("d"), following)
    }
}
