package its.reasoner.operators

import its.model.definition.types.Clazz
import its.reasoner.ReasoningException
import its.reasoner.TypingException
import kotlin.test.Test
import kotlin.test.assertEquals

class TypingOperatorsTest : OperatorTestBase() {

    /** Класс объекта - его непосредственный класс. */
    @Test
    fun classOfObjectIsItsDirectClass() {
        assertEquals(Clazz("item"), eval("obj:a.class()"))
        assertEquals(Clazz("glass"), eval("obj:b.class()"))
        assertEquals(true, eval("obj:b.class() == class:glass"))
    }

    /** Проверка класса учитывает наследование. */
    @Test
    fun classCheckRespectsInheritance() {
        assertEquals(true, eval("obj:b is class:glass"))
        assertEquals(true, eval("obj:b is class:item"))
        assertEquals(false, eval("obj:a is class:glass"))
        assertEquals(false, eval("obj:box1 is class:item"))
    }

    /** Приведение к классу возвращает тот же объект, если он является экземпляром класса. */
    @Test
    fun castReturnsObjectWhenItIsInstance() {
        assertEquals(obj("b"), eval("obj:b as class:glass"))
        assertEquals(obj("b"), eval("obj:b as class:item"))
        assertEquals(1, eval("(obj:a as class:item).weight"))
    }

    /** Приведение к чужому классу - ошибка типизации. */
    @Test
    fun castToForeignClassFails() {
        evalFails<TypingException>("obj:a as class:glass")
        evalFails<TypingException>("obj:box1 as class:item")
    }

    /** Ссылка на несуществующий класс - ошибка. */
    @Test
    fun unknownClassReferenceFails() {
        evalFails<ReasoningException>("obj:a is class:nothing")
        evalFails<ReasoningException>("obj:a as class:nothing")
    }

    /** Новый объект создаётся со свойствами и связями из вычисленных выражений. */
    @Test
    fun newObjectIsCreatedWithPropertiesAndLinks() {
        // Act.
        val created = eval("+obj:item({ weight = obj:e.weight ; price = 0.5 ; color = Color:green ; sold = false ; next(obj:e) ; })")

        // Assert.
        assertEquals(obj("auto_0"), created)
        assertEquals(8, model.objects.size)
        assertEquals(5, eval("obj:auto_0.weight"))
        assertEquals(Clazz("item"), eval("obj:auto_0.class()"))
        assertEquals(obj("e"), eval("obj:auto_0->next"))
        assertEquals(obj("auto_0"), eval("obj:e->prev"))
    }

    /** Имена новых объектов не повторяются. */
    @Test
    fun newObjectNamesAreUnique() {
        // Act.
        val first = eval("+obj:box({ capacity = 1 ; })")
        val second = eval("+obj:box({ capacity = 2 ; })")

        // Assert.
        assertEquals(obj("auto_0"), first)
        assertEquals(obj("auto_1"), second)
        assertEquals(objects("box1", "box2", "auto_0", "auto_1"), model.objects.filter { it.isInstanceOf("box") }.map { it.reference })
    }

    /** Новый объект сразу виден кванторам и поиску. */
    @Test
    fun newObjectIsVisibleToSearches() {
        // Act.
        eval("+obj:glass({ weight = 9 ; price = 9.0 ; color = Color:red ; sold = false ; })")

        // Assert.
        assertEquals(obj("auto_0"), eval($$"find item i { $i.weight == 9 }"))
        assertEquals(true, eval($$"forAny glass g { $g.weight == 9 }"))
        assertEquals(3, model.objects.count { it.isInstanceOf("glass") })
    }

    /** Новый объект, нарушающий ограничения модели, не добавляется. */
    @Test
    fun invalidNewObjectIsRejected() {
        // Act.
        evalFails<Exception>("+obj:item({ weight = \"heavy\" ; })")

        // Assert.
        assertEquals(7, model.objects.size)
    }
}
