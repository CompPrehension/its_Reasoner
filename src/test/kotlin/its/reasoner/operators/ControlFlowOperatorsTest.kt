package its.reasoner.operators

import its.reasoner.UnknownVariableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ControlFlowOperatorsTest : OperatorTestBase() {

    /** Блок вычисляет выражения по порядку и возвращает значение последнего. */
    @Test
    fun blockReturnsLastValue() {
        // Act.
        val result = eval("{ obj:a.weight = 10 ; obj:a.sold = true ; obj:a.weight }")

        // Assert.
        assertEquals(10, result)
        assertEquals(true, eval("obj:a.sold"))
    }

    /** Пустой блок - ошибка. */
    @Test
    fun emptyBlockFails() {
        evalFails<NoSuchElementException>("{ }")
    }

    /** Тернарный оператор вычисляет только выбранную ветвь. */
    @Test
    fun ternaryEvaluatesOnlyChosenBranch() {
        assertEquals(1, eval($$"true ? 1 : $undefined"))
        assertEquals(2, eval($$"false ? $undefined : 2"))
        evalFails<UnknownVariableException>($$"false ? 1 : $undefined")
    }

    /** Тернарный оператор правоассоциативен: вложенные условия читаются как цепочка. */
    @Test
    fun ternaryChainIsRightAssociative() {
        assertEquals("c", eval("obj:c.weight == 1 ? \"a\" : obj:c.weight == 2 ? \"b\" : obj:c.weight == 3 ? \"c\" : \"other\""))
        assertEquals("other", eval("obj:e.weight == 1 ? \"a\" : obj:e.weight == 2 ? \"b\" : \"other\""))
    }

    /** Условие if без else возвращает null независимо от выполненной ветви. */
    @Test
    fun ifWithoutElseReturnsNull() {
        assertNull(eval("if (true) 1"))
        assertNull(eval("if (false) 1"))
    }

    /** Условие if с else возвращает значение выполненной ветви. */
    @Test
    fun ifWithElseReturnsBranchValue() {
        assertEquals(1, eval("if (true) 1 else 2"))
        assertEquals(2, eval("if (false) 1 else 2"))
    }

    /** Условие с опциональным логическим значением: null и false не выполняют ветвь then. */
    @Test
    fun optionalBoolConditionInIf() {
        assertEquals(1, eval("if (OptionalBool:`true`) 1 else 2"))
        assertEquals(2, eval("if (OptionalBool:`false`) 1 else 2"))
        assertEquals(2, eval("if (OptionalBool:`null`) 1 else 2"))
    }

    /** Изменения в блоке видны последующим выражениям того же блока. */
    @Test
    fun blockStatementsSeeEarlierMutations() {
        assertEquals(true, eval($$"{ obj:e.sold = true ; forAll item i { $i.sold or $i.weight < 5 } }"))
    }
}
