package its.reasoner.operators

import its.model.definition.types.Comparison
import kotlin.test.Test
import kotlin.test.assertEquals

class ComparisonOperatorsTest : OperatorTestBase() {

    /** Трёхзначное сравнение чисел, в том числе целых с дробными. */
    @Test
    fun threeWayCompareOrdersNumbers() {
        assertEquals(Comparison.Values.Less, eval("(1).compare(2)"))
        assertEquals(Comparison.Values.Greater, eval("(2.5).compare(2)"))
        assertEquals(Comparison.Values.Equal, eval("(2).compare(2.0)"))
    }

    /** Трёхзначное сравнение читает числа из свойств объектов. */
    @Test
    fun threeWayCompareReadsProperties() {
        assertEquals(Comparison.Values.Less, eval("obj:a.weight.compare(obj:b.weight)"))
        assertEquals(Comparison.Values.Greater, eval("obj:e.price.compare(obj:d.price)"))
    }

    /** Равенство чисел не зависит от целого или дробного представления. */
    @Test
    fun numericEqualityIgnoresRepresentation() {
        assertEquals(true, eval("1 == 1.0"))
        assertEquals(false, eval("1 != 1.0"))
        assertEquals(false, eval("1 == 2"))
        assertEquals(true, eval("1 != 2"))
    }

    /** Равенство строк, перечислений и ссылок на объекты - по значению. */
    @Test
    fun equalityComparesValuesAndReferences() {
        assertEquals(true, eval("\"a\" == \"a\""))
        assertEquals(false, eval("\"a\" == \"b\""))
        assertEquals(true, eval("Color:red == Color:red"))
        assertEquals(false, eval("Color:red == Color:blue"))
        assertEquals(true, eval("obj:a == obj:a"))
        assertEquals(false, eval("obj:a == obj:b"))
        assertEquals(true, eval("obj:a.color == Color:red"))
    }

    /** Значения разных типов не равны, но и не вызывают ошибку. */
    @Test
    fun differentTypesAreNotEqual() {
        assertEquals(false, eval("1 == \"1\""))
        assertEquals(false, eval("true == 1"))
        assertEquals(true, eval("obj:a != class:item"))
    }

    /** Порядковые сравнения чисел. */
    @Test
    fun orderingComparisons() {
        assertEquals(true, eval("1 < 2"))
        assertEquals(false, eval("2 < 2"))
        assertEquals(true, eval("2 <= 2"))
        assertEquals(true, eval("2.5 > 2"))
        assertEquals(true, eval("2 >= 2.0"))
        assertEquals(false, eval("obj:a.weight > obj:b.weight"))
    }

    /** Порядковое сравнение не чисел - ошибка вычисления. */
    @Test
    fun orderingNonNumbersFails() {
        evalFails<ClassCastException>("\"a\" < \"b\"")
        evalFails<ClassCastException>("obj:a < obj:b")
    }

    /** Трёхзначное сравнение не чисел - ошибка вычисления. */
    @Test
    fun threeWayCompareOfNonNumbersFails() {
        evalFails<ClassCastException>("(\"a\").compare(1)")
    }

    /** Опциональное логическое значение равно обычному логическому с тем же смыслом. */
    @Test
    fun optionalBoolEqualsPlainBool() {
        assertEquals(true, eval("OptionalBool:`true` == true"))
        assertEquals(true, eval("true == OptionalBool:`true`"))
        assertEquals(true, eval("OptionalBool:`false` == false"))
        assertEquals(false, eval("OptionalBool:`true` == false"))
        assertEquals(true, eval("OptionalBool:`true` != false"))
    }

    /** Опциональное null не равно ничему, даже самому себе. */
    @Test
    fun optionalNullEqualsNothing() {
        assertEquals(false, eval("OptionalBool:`null` == OptionalBool:`null`"))
        assertEquals(false, eval("OptionalBool:`null` == true"))
        assertEquals(false, eval("false == OptionalBool:`null`"))
        assertEquals(true, eval("OptionalBool:`null` != OptionalBool:`null`"))
    }

    /** Опциональные значения сравниваются между собой как перечисления. */
    @Test
    fun optionalBoolsCompareAsEnums() {
        assertEquals(true, eval("OptionalBool:`true` == OptionalBool:`true`"))
        assertEquals(false, eval("OptionalBool:`true` == OptionalBool:`false`"))
    }

    /** Результат null (например, неудачный поиск) равен только другому null и не вызывает ошибку. */
    @Test
    fun nullResultEqualsOnlyNull() {
        assertEquals(false, eval("(find item i { false }) == obj:a"))
        assertEquals(true, eval("(find item i { false }) != obj:a"))
        assertEquals(true, eval("(find item i { false }) == (find item i { false })"))
    }
}
