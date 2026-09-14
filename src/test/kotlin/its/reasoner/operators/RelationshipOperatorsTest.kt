package its.reasoner.operators

import its.reasoner.AmbiguousObjectException
import its.reasoner.ReasoningException
import its.reasoner.ReasoningMisuseException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Отношения предметов: a -> b -> c -> d, e в стороне; box1 держит a и b, box2 держит d.
 */
class RelationshipOperatorsTest : OperatorTestBase() {

    /** Переход по базовой связи и по противоположной ей. */
    @Test
    fun getByBaseAndOppositeRelationship() {
        assertEquals(obj("b"), eval("obj:a->next"))
        assertEquals(obj("a"), eval("obj:b->prev"))
        assertEquals(obj("box1"), eval("obj:a->storedIn"))
        assertEquals(obj("box2"), eval("obj:d->storedIn"))
    }

    /** Переход по отсутствующей связи - ошибка. */
    @Test
    fun getByMissingLinkFails() {
        evalFails<AmbiguousObjectException>("obj:d->next")
        evalFails<AmbiguousObjectException>("obj:a->prev")
        evalFails<AmbiguousObjectException>("obj:c->storedIn")
    }

    /** Переход при нескольких подходящих связях - ошибка. */
    @Test
    fun getByMultipleLinksFails() {
        evalFails<AmbiguousObjectException>("obj:box1->holds")
        evalFails<AmbiguousObjectException>("obj:a->likes")
    }

    /** Переход по связи с параметрами выбирает связь по значению параметра. */
    @Test
    fun getByRelationshipWithParameters() {
        assertEquals(obj("c"), eval("obj:a->likes<3>"))
        assertEquals(obj("d"), eval("obj:a->likes<strength = 1>"))
        evalFails<AmbiguousObjectException>("obj:a->likes<2>")
    }

    /** Переход по зависимому отношению на шкале невозможен без указания объекта. */
    @Test
    fun getByScalarDependentRelationshipFails() {
        evalFails<ReasoningMisuseException>("obj:a->leadsTo")
        evalFails<ReasoningMisuseException>("obj:b->isBetween")
    }

    /** Переход от null-объекта - ошибка с указанием действия. */
    @Test
    fun getByRelationshipOfNullObjectFails() {
        // Act.
        val error = evalFails<ReasoningException>("(find item i { false })->next")

        // Assert.
        assertEquals(true, error.message!!.contains("get relationship 'next'"))
    }

    /** Проверка базовой и противоположной связи. */
    @Test
    fun checkBaseAndOppositeLink() {
        assertEquals(true, eval("obj:a=>next(obj:b)"))
        assertEquals(false, eval("obj:a=>next(obj:c)"))
        assertEquals(false, eval("obj:b=>next(obj:a)"))
        assertEquals(true, eval("obj:b=>prev(obj:a)"))
        assertEquals(false, eval("obj:a=>prev(obj:b)"))
        assertEquals(true, eval("obj:box1=>holds(obj:b)"))
        assertEquals(true, eval("obj:b=>storedIn(obj:box1)"))
        assertEquals(false, eval("obj:c=>storedIn(obj:box1)"))
    }

    /** Транзитивное отношение и противоположное ему на линейной шкале. */
    @Test
    fun checkTransitiveLinkOnLinearScale() {
        assertEquals(true, eval("obj:a=>leadsTo(obj:b)"))
        assertEquals(true, eval("obj:a=>leadsTo(obj:d)"))
        assertEquals(false, eval("obj:d=>leadsTo(obj:a)"))
        assertEquals(false, eval("obj:a=>leadsTo(obj:a)"))
        assertEquals(true, eval("obj:d=>follows(obj:a)"))
        assertEquals(false, eval("obj:a=>follows(obj:d)"))
    }

    /** Объект вне шкалы не связан транзитивно ни с кем. */
    @Test
    fun isolatedObjectHasNoTransitiveLinks() {
        assertEquals(false, eval("obj:a=>leadsTo(obj:e)"))
        assertEquals(false, eval("obj:e=>leadsTo(obj:a)"))
        assertEquals(false, eval("obj:e=>follows(obj:a)"))
        assertEquals(false, eval("obj:b=>isBetween(obj:a, obj:e)"))
    }

    /** Отношение "между" симметрично относительно границ и учитывает транзитивность. */
    @Test
    fun checkBetweenLink() {
        assertEquals(true, eval("obj:b=>isBetween(obj:a, obj:c)"))
        assertEquals(true, eval("obj:b=>isBetween(obj:c, obj:a)"))
        assertEquals(true, eval("obj:c=>isBetween(obj:a, obj:d)"))
        assertEquals(false, eval("obj:a=>isBetween(obj:b, obj:c)"))
        assertEquals(false, eval("obj:b=>isBetween(obj:a, obj:b)"))
        assertEquals(false, eval("obj:d=>isBetween(obj:a, obj:c)"))
    }

    /** Отношения "ближе" и "дальше": X=>isCloser(A, B) - X ближе к A, чем B; X=>isFurther(A, B) - X дальше от A, чем B. */
    @Test
    fun checkCloserAndFurtherLinks() {
        assertEquals(true, eval("obj:b=>isCloser(obj:a, obj:d)"))
        assertEquals(false, eval("obj:c=>isCloser(obj:a, obj:b)"))
        assertEquals(true, eval("obj:c=>isCloser(obj:d, obj:a)"))
        assertEquals(true, eval("obj:d=>isFurther(obj:a, obj:b)"))
        assertEquals(false, eval("obj:b=>isFurther(obj:a, obj:d)"))
        assertEquals(true, eval("obj:a=>isFurther(obj:d, obj:c)"))
    }

    /** Проверка связи с параметрами: без параметров подходит любая связь, с параметрами - только совпадающая. */
    @Test
    fun checkLinkWithParameters() {
        assertEquals(true, eval("obj:a=>likes(obj:c)"))
        assertEquals(true, eval("obj:a=>likes<3>(obj:c)"))
        assertEquals(false, eval("obj:a=>likes<1>(obj:c)"))
        assertEquals(true, eval("obj:a=>likes<strength = 1>(obj:d)"))
        assertEquals(false, eval("obj:a=>likes(obj:b)"))
    }

    /** Проверка наличия хоть какой-нибудь связи: для базовых и зависимых отношений. */
    @Test
    fun checkLinkWithAnyObject() {
        assertEquals(true, eval("obj:a=>next()"))
        assertEquals(false, eval("obj:d=>next()"))
        assertEquals(true, eval("obj:b=>prev()"))
        assertEquals(false, eval("obj:a=>prev()"))
        assertEquals(true, eval("obj:a=>leadsTo()"))
        assertEquals(false, eval("obj:d=>leadsTo()"))
        assertEquals(false, eval("obj:e=>leadsTo()"))
        assertEquals(true, eval("obj:b=>isBetween()"))
        assertEquals(false, eval("obj:a=>isBetween()"))
    }

    /** Субъект проецируется на класс отношения: коробка связана, если связаны все её предметы. */
    @Test
    fun subjectIsProjectedOntoRelationshipClass() {
        assertEquals(true, eval("obj:box1=>leadsTo(obj:d)"))
        assertEquals(true, eval("obj:box1=>leadsTo(obj:c)"))
        assertEquals(false, eval("obj:box1=>leadsTo(obj:b)"))
        assertEquals(false, eval("obj:box2=>next()"))
        assertEquals(true, eval("obj:box1=>next()"))
    }

    /** Объекты связи тоже проецируются на класс отношения. */
    @Test
    fun objectsAreProjectedOntoRelationshipClass() {
        assertEquals(true, eval("obj:a=>leadsTo(obj:box2)"))
        assertEquals(false, eval("obj:c=>leadsTo(obj:box1)"))
        assertEquals(true, eval("obj:box1=>leadsTo(obj:box2)"))
    }

    /** Параметр связи читается у базовой связи по указанным объектам. */
    @Test
    fun relationshipParameterIsRead() {
        assertEquals(3, eval("obj:a=>likes(obj:c).strength"))
        assertEquals(1, eval("obj:a=>likes(obj:d).strength"))
        evalFails<AmbiguousObjectException>("obj:a=>likes(obj:b).strength")
    }

    /** Добавленная связь видна и по базовому, и по зависимым отношениям. */
    @Test
    fun addedLinkIsVisibleThroughDependentRelationships() {
        // Act.
        eval("obj:d +=> next(obj:e)")

        // Assert.
        assertEquals(obj("e"), eval("obj:d->next"))
        assertEquals(obj("d"), eval("obj:e->prev"))
        assertEquals(true, eval("obj:a=>leadsTo(obj:e)"))
        assertEquals(true, eval("obj:d=>isBetween(obj:a, obj:e)"))
    }

    /** Связь добавляется с параметрами. */
    @Test
    fun linkIsAddedWithParameters() {
        // Act.
        eval("obj:b +=> likes<2>(obj:e)")

        // Assert.
        assertEquals(true, eval("obj:b=>likes<2>(obj:e)"))
        assertEquals(2, eval("obj:b=>likes(obj:e).strength"))
    }

    /** Удалённая связь исчезает из базового и зависимых отношений. */
    @Test
    fun removedLinkDisappearsEverywhere() {
        // Act.
        eval("obj:b -=> next(obj:c)")

        // Assert.
        assertEquals(false, eval("obj:b=>next(obj:c)"))
        assertEquals(false, eval("obj:a=>leadsTo(obj:d)"))
        assertEquals(true, eval("obj:a=>leadsTo(obj:b)"))
        assertEquals(true, eval("obj:c=>leadsTo(obj:d)"))
        evalFails<AmbiguousObjectException>("obj:c->prev")
    }

    /** Удаление связи с параметрами затрагивает только связь с этими параметрами. */
    @Test
    fun removalWithParametersIsSelective() {
        // Act.
        eval("obj:a -=> likes<3>(obj:c)")

        // Assert.
        assertEquals(false, eval("obj:a=>likes(obj:c)"))
        assertEquals(true, eval("obj:a=>likes(obj:d)"))
    }

    /** Удаление отсутствующей связи ничего не меняет. */
    @Test
    fun removingMissingLinkIsNoop() {
        // Act.
        eval("obj:a -=> next(obj:c)")

        // Assert.
        assertEquals(true, eval("obj:a=>next(obj:b)"))
    }
}
