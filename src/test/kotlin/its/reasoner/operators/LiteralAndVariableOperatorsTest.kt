package its.reasoner.operators

import its.model.definition.types.Clazz
import its.model.definition.types.EnumValue
import its.reasoner.UnknownVariableException
import kotlin.test.Test
import kotlin.test.assertEquals

class LiteralAndVariableOperatorsTest : OperatorTestBase() {

    override fun variables() = mapOf("X" to "a")

    /** Литералы значений вычисляются в значения соответствующих типов. */
    @Test
    fun valueLiteralsEvaluateToTypedValues() {
        assertEquals(true, eval("true"))
        assertEquals(false, eval("false"))
        assertEquals(42, eval("42"))
        assertEquals(2.5, eval("2.5"))
        assertEquals("text", eval("\"text\""))
        assertEquals(EnumValue("Color", "red"), eval("Color:red"))
    }

    /** Ссылки на класс и объект вычисляются в ссылки без проверки существования. */
    @Test
    fun domainReferenceLiteralsEvaluateToReferences() {
        assertEquals(Clazz("item"), eval("class:item"))
        assertEquals(obj("a"), eval("obj:a"))
        assertEquals(Clazz("nothing"), eval("class:nothing"))
        assertEquals(obj("nothing"), eval("obj:nothing"))
    }

    /** Переменная контекста читается из переданного контекста. */
    @Test
    fun contextVariableIsReadFromContext() {
        // Act.
        val result = eval($$"$n", mapOf("n" to obj("b")))

        // Assert.
        assertEquals(obj("b"), result)
    }

    /** Отсутствующая в контексте переменная - ошибка. */
    @Test
    fun missingContextVariableFails() {
        evalFails<UnknownVariableException>($$"$missing")
    }

    /** Переменная дерева решений читается из ситуации. */
    @Test
    fun decisionTreeVariableIsReadFromSituation() {
        assertEquals(obj("a"), eval("X"))
    }

    /** Отсутствующая переменная дерева решений - ошибка. */
    @Test
    fun missingDecisionTreeVariableFails() {
        evalFails<UnknownVariableException>("Y")
    }

    /** Присваивание переменной дерева меняет её значение в ситуации. */
    @Test
    fun decisionTreeVariableAssignment() {
        // Act.
        eval("X = obj:a->next")

        // Assert.
        assertEquals(obj("b"), eval("X"))
        assertEquals(obj("b"), situation.decisionTreeVariables["X"])
    }

    /** Присваивание необъявленной переменной дерева - ошибка. */
    @Test
    fun assigningUndeclaredDecisionTreeVariableFails() {
        evalFails<UnknownVariableException>("Y = obj:a")
        assertEquals(setOf("X"), situation.decisionTreeVariables.keys)
    }

    /** Переменной дерева можно присвоить только объект. */
    @Test
    fun decisionTreeVariableAcceptsOnlyObjects() {
        evalFails<ClassCastException>("X = 1")
        assertEquals(obj("a"), eval("X"))
    }
}
