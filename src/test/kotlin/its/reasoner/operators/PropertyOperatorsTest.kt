package its.reasoner.operators

import its.model.definition.InvalidDomainDefinitionException
import its.model.definition.types.EnumValue
import its.reasoner.ReasonerFixtures.obj
import its.reasoner.ReasoningException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PropertyOperatorsTest : OperatorTestBase() {

    /** Свойства объекта читаются со значениями своих типов. */
    @Test
    fun objectPropertiesAreReadWithTheirTypes() {
        assertEquals(1, eval("obj:a.weight"))
        assertEquals(1.5, eval("obj:a.price"))
        assertEquals(EnumValue("Color", "red"), eval("obj:a.color"))
        assertEquals(false, eval("obj:a.sold"))
    }

    /** Свойство класса читается через объект: значение по умолчанию либо переопределённое в подклассе. */
    @Test
    fun classPropertiesAreInheritedAndOverridden() {
        assertEquals("generic", eval("obj:a.category"))
        assertEquals(false, eval("obj:a.fragile"))
        assertEquals("glass", eval("obj:b.category"))
        assertEquals(true, eval("obj:b.fragile"))
    }

    /** Свойство с параметрами читается по значению параметра. */
    @Test
    fun parametrizedPropertyIsReadByParameter() {
        assertEquals("a", eval("obj:a.label<Lang:en>"))
        assertEquals("а", eval("obj:a.label<Lang:ru>"))
        assertEquals("a", eval("obj:a.label<lang = Lang:en>"))
    }

    /** Чтение не заданного значения параметризованного свойства - ошибка. */
    @Test
    fun readingUndefinedParametrizedValueFails() {
        assertFailsWith<Exception> { eval("obj:b.label<Lang:ru>") }
    }

    /** Присваивание меняет значение свойства объекта, что видно последующим чтениям. */
    @Test
    fun assignmentChangesObjectProperty() {
        // Act.
        eval("obj:a.weight = 7")
        eval("obj:a.color = Color:blue")

        // Assert.
        assertEquals(7, eval("obj:a.weight"))
        assertEquals(EnumValue("Color", "blue"), eval("obj:a.color"))
        assertEquals(7, model.obj("a").getPropertyValue("weight"))
    }

    /** Присваивание параметризованного свойства меняет только значение для этого параметра. */
    @Test
    fun assignmentToParametrizedPropertyIsPerParameter() {
        // Act.
        eval("obj:b.label<Lang:ru> = \"б\"")

        // Assert.
        assertEquals("б", eval("obj:b.label<Lang:ru>"))
        assertEquals("b", eval("obj:b.label<Lang:en>"))
    }

    /** Присваиваемое значение вычисляется из выражения. */
    @Test
    fun assignedValueIsComputed() {
        // Act.
        eval("obj:a.weight = obj:e.weight")
        eval("obj:a.sold = obj:a.weight > 4")

        // Assert.
        assertEquals(5, eval("obj:a.weight"))
        assertEquals(true, eval("obj:a.sold"))
    }

    /** Присваивание через переменную квантора меняет объекты, отобранные селектором. */
    @Test
    fun assignmentThroughQuantifierVariable() {
        // Act.
        eval($$"forAll item i [ $i.color == Color:blue ] { $i.sold = true }")

        // Assert.
        assertEquals(listOf(false, true, true, true, true), listOf("a", "b", "c", "d", "e").map { eval("obj:$it.sold") })
    }

    /** Присваивание значения неподходящего типа отвергается, свойство не меняется. */
    @Test
    fun assignmentOfWrongTypeIsRejected() {
        // Act.
        evalFails<InvalidDomainDefinitionException>("obj:a.weight = \"heavy\"")

        // Assert.
        assertEquals(1, eval("obj:a.weight"))
    }

    /** Чтение свойства у null-объекта - ошибка с указанием действия. */
    @Test
    fun readingPropertyOfNullObjectFails() {
        // Act.
        val error = evalFails<ReasoningException>("(find item i { false }).weight")

        // Assert.
        assertEquals(true, error.message!!.contains("read property 'weight'"))
    }

    /** Присваивание свойства null-объекту - ошибка с указанием действия. */
    @Test
    fun assigningPropertyOfNullObjectFails() {
        // Act.
        val error = evalFails<ReasoningException>("(find item i { false }).weight = 1")

        // Assert.
        assertEquals(true, error.message!!.contains("assign property 'weight'"))
    }

    /** Чтение свойства у несуществующего объекта - ошибка. */
    @Test
    fun readingPropertyOfUnknownObjectFails() {
        evalFails<ReasoningException>("obj:nothing.weight")
    }

    /** Чтение несуществующего свойства - ошибка. */
    @Test
    fun readingUnknownPropertyFails() {
        assertFailsWith<Exception> { eval("obj:a.nothing") }
    }
}
