package its.reasoner.operators

import its.reasoner.UnknownVariableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogicalOperatorsTest : OperatorTestBase() {

    /** Таблицы истинности and, or, not. */
    @Test
    fun truthTables() {
        assertEquals(true, eval("true and true"))
        assertEquals(false, eval("true and false"))
        assertEquals(true, eval("false or true"))
        assertEquals(false, eval("false or false"))
        assertEquals(false, eval("not true"))
        assertEquals(true, eval("not false"))
    }

    /** Второй операнд and/or не вычисляется, если результат уже определён первым. */
    @Test
    fun andOrShortCircuit() {
        assertEquals(false, eval($$"false and $undefined"))
        assertEquals(true, eval($$"true or $undefined"))
        evalFails<UnknownVariableException>($$"true and $undefined")
        evalFails<UnknownVariableException>($$"false or $undefined")
    }

    /** Опциональное логическое значение в роли условия: true - истина, false и null - ложь. */
    @Test
    fun optionalBoolActsAsCondition() {
        assertEquals(true, eval("OptionalBool:`true` and true"))
        assertEquals(false, eval("OptionalBool:`false` or false"))
        assertEquals(false, eval("OptionalBool:`null` or false"))
        assertEquals(true, eval("not OptionalBool:`null`"))
    }

    /** Не логическое значение в роли условия - ошибка. */
    @Test
    fun nonBooleanConditionFails() {
        evalFails<ClassCastException>("1 and true")
        evalFails<ClassCastException>("not \"yes\"")
    }

    /** Квантор существования: истина при первом подходящем объекте, иначе ложь. */
    @Test
    fun existenceQuantifier() {
        assertEquals(true, eval($$"forAny item i { $i.weight > 4 }"))
        assertEquals(false, eval($$"forAny item i { $i.weight > 5 }"))
        assertEquals(true, eval($$"forAny glass g { $g.sold }"))
    }

    /** Квантор всеобщности: истина, если условие выполнено для каждого объекта. */
    @Test
    fun universalQuantifier() {
        assertEquals(true, eval($$"forAll item i { $i.weight > 0 }"))
        assertEquals(false, eval($$"forAll item i { $i.sold }"))
        assertEquals(true, eval($$"forAll glass g { $g.sold }"))
    }

    /** Селектор ограничивает множество объектов квантора. */
    @Test
    fun quantifierSelectorFiltersObjects() {
        assertEquals(true, eval($$"forAll item i [ $i.sold ] { $i is class:glass }"))
        assertEquals(false, eval($$"forAny item i [ $i.sold ] { $i.color == Color:blue }"))
        assertEquals(false, eval($$"forAll item i [ $i.weight > 2 ] { $i.sold }"))
    }

    /** На пустом множестве существование ложно, а всеобщность истинна. */
    @Test
    fun quantifiersOverEmptySet() {
        assertEquals(false, eval($$"forAny item i [ false ] { true }"))
        assertEquals(true, eval($$"forAll item i [ false ] { false }"))
        assertEquals(false, eval($$"forAny box b [ $b.capacity > 10 ] { true }"))
    }

    /** Квантор существования прекращает перебор на первом подходящем объекте. */
    @Test
    fun existenceStopsAtFirstMatch() {
        assertEquals(true, eval($$"forAny item i { $i.weight == 1 or $undefined }"))
        evalFails<UnknownVariableException>($$"forAny item i { $i.weight == 2 or $undefined }")
    }

    /** Квантор всеобщности прекращает перебор на первом неподходящем объекте. */
    @Test
    fun universalStopsAtFirstMismatch() {
        assertEquals(false, eval($$"forAll item i { $i.weight != 1 and $undefined }"))
        evalFails<UnknownVariableException>($$"forAll item i { $i.weight != 2 and $undefined }")
    }

    /** Кванторы в роли цикла: если тело не логическое, всеобщность обходит все объекты и возвращает null. */
    @Test
    fun universalQuantifierAsLoopReturnsNull() {
        // Act.
        val result = eval($$"forAll item i { $i.sold = true }")

        // Assert.
        assertNull(result)
        assertEquals(true, eval($$"forAll item i { $i.sold }"))
    }

    /** Квантор существования в роли цикла останавливается на первом объекте и возвращает null. */
    @Test
    fun existenceQuantifierAsLoopStopsAtFirstObject() {
        // Act.
        val result = eval($$"forAny item i { $i.sold = true }")

        // Assert.
        assertNull(result)
        assertEquals(listOf(true, true, false, true, false), listOf("a", "b", "c", "d", "e").map { eval("obj:$it.sold") })
    }

    /** Переменные вложенных кванторов видны во внутреннем теле, внешняя переменная - тоже. */
    @Test
    fun nestedQuantifiersSeeOuterVariables() {
        assertEquals(true, eval($$"forAny item i { forAny item j { $i != $j and $i.weight == 1 and $j.weight == 5 } }"))
        assertEquals(false, eval($$"forAll item i { forAny item j { $j.weight > $i.weight } }"))
    }

    /** Переменная квантора недоступна вне его тела. */
    @Test
    fun quantifierVariableIsScoped() {
        evalFails<UnknownVariableException>($$"(forAny item i { true }) and $i.sold")
    }
}
