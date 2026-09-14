package its.reasoner.operators

import its.model.definition.types.EnumValue
import its.reasoner.AmbiguousObjectException
import its.reasoner.ReasonerFixtures
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Дерево выражения plus(x, mul(y, z)) как частичная шкала isOperandOf с параметром стороны,
 * а также n-арное отношение с одинаковыми классами объектов.
 */
class PartialScaleRelationshipTest : OperatorTestBase() {

    override fun domain() = ReasonerFixtures.domain("""
        enum Side { left, right }

        class expr {
            rel isOperandOf<side: Side>(expr) : partial ;
            rel hasOperand(expr) : opposite to isOperandOf ;
            rel isInOperandOf(expr) : transitive to isOperandOf ;
            rel contains(expr) : opposite to isInOperandOf ;
            rel triangle(expr, expr) ;
        }

        obj plus : expr { triangle(x, mul) ; }
        obj mul : expr { isOperandOf<Side:right>(plus) ; }
        obj x : expr { isOperandOf<Side:left>(plus) ; }
        obj y : expr { isOperandOf<Side:left>(mul) ; }
        obj z : expr { isOperandOf<Side:right>(mul) ; }
    """)

    /** Переход вверх по частичной шкале однозначен, вниз - неоднозначен. */
    @Test
    fun getByPartialScaleLinks() {
        assertEquals(obj("mul"), eval("obj:y->isOperandOf"))
        assertEquals(obj("plus"), eval("obj:mul->isOperandOf"))
        evalFails<AmbiguousObjectException>("obj:plus->hasOperand")
        evalFails<AmbiguousObjectException>("obj:y->hasOperand")
    }

    /** Переход по противоположной связи с параметром выбирает ветвь. */
    @Test
    fun getByOppositeLinkWithParameter() {
        assertEquals(obj("x"), eval("obj:plus->hasOperand<Side:left>"))
        assertEquals(obj("mul"), eval("obj:plus->hasOperand<Side:right>"))
        assertEquals(obj("z"), eval("obj:mul->hasOperand<side = Side:right>"))
    }

    /** Транзитивная связь на частичной шкале проходит через промежуточные узлы, но не между ветвями. */
    @Test
    fun checkTransitiveLinkOnPartialScale() {
        assertEquals(true, eval("obj:y=>isInOperandOf(obj:mul)"))
        assertEquals(true, eval("obj:y=>isInOperandOf(obj:plus)"))
        assertEquals(false, eval("obj:y=>isInOperandOf(obj:x)"))
        assertEquals(false, eval("obj:plus=>isInOperandOf(obj:y)"))
        assertEquals(true, eval("obj:plus=>contains(obj:z)"))
        assertEquals(false, eval("obj:x=>contains(obj:z)"))
    }

    /** Параметр противоположной связи берётся из базовой связи. */
    @Test
    fun parameterOfOppositeLinkComesFromBaseLink() {
        assertEquals(EnumValue("Side", "right"), eval("obj:plus=>hasOperand(obj:mul).side"))
        assertEquals(EnumValue("Side", "left"), eval("obj:mul=>hasOperand(obj:y).side"))
        assertEquals(EnumValue("Side", "left"), eval("obj:x=>isOperandOf(obj:plus).side"))
    }

    /** Параметр транзитивной связи берётся из связи, ведущей к самому верхнему объекту. */
    @Test
    fun parameterOfTransitiveLinkComesFromTopmostLink() {
        assertEquals(EnumValue("Side", "right"), eval("obj:y=>isInOperandOf(obj:plus).side"))
        assertEquals(EnumValue("Side", "left"), eval("obj:y=>isInOperandOf(obj:mul).side"))
    }

    /** Проверка связи с параметром на частичной шкале. */
    @Test
    fun checkLinkWithParameterOnPartialScale() {
        assertEquals(true, eval("obj:plus=>hasOperand<Side:right>(obj:mul)"))
        assertEquals(false, eval("obj:plus=>hasOperand<Side:left>(obj:mul)"))
        assertEquals(true, eval("obj:mul=>hasOperand()"))
        assertEquals(false, eval("obj:x=>hasOperand()"))
    }

    /** Связи n-арного отношения с одинаковыми классами объектов не зависят от порядка объектов. */
    @Test
    fun unorderedLinksMatchInAnyObjectOrder() {
        assertEquals(true, eval("obj:plus=>triangle(obj:x, obj:mul)"))
        assertEquals(true, eval("obj:plus=>triangle(obj:mul, obj:x)"))
        assertEquals(false, eval("obj:plus=>triangle(obj:x, obj:x)"))
        assertEquals(false, eval("obj:plus=>triangle(obj:x, obj:y)"))
    }

    /** Удаление связи n-арного отношения не зависит от порядка объектов. */
    @Test
    fun unorderedLinkIsRemovedInAnyObjectOrder() {
        // Act.
        eval("obj:plus -=> triangle(obj:mul, obj:x)")

        // Assert.
        assertEquals(false, eval("obj:plus=>triangle(obj:x, obj:mul)"))
    }
}
