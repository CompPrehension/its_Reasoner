package its.reasoner.operators

import its.model.definition.types.Obj
import its.model.expressions.literals.IntegerLiteral
import its.model.expressions.literals.VariableLiteral
import its.model.expressions.operators.CompareWithComparisonOperator
import its.model.expressions.operators.ExistenceQuantifier
import its.model.expressions.operators.GetPropertyValue
import its.reasoner.ReasonerFixtures
import its.reasoner.ReasonerFixtures.expression
import its.reasoner.ReasoningException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpressionTraceTest {

    private val situation = ReasonerFixtures.situation(ReasonerFixtures.items(), "X" to "a")

    private fun traced() = DomainInterpreterReasoner(situation, collectExpressionTrace = true)

    /** Без сбора трассы она остаётся пустой. */
    @Test
    fun traceIsEmptyWhenNotCollected() {
        // Arrange.
        val reasoner = DomainInterpreterReasoner(situation)

        // Act.
        reasoner.evalWithTrace(expression("obj:a.weight > 0"))

        // Assert.
        assertEquals(emptyList(), reasoner.expressionTrace)
    }

    /** Трасса повторяет структуру выражения: значения аннотируются у операторов и переменных, но не у литералов. */
    @Test
    fun traceMirrorsExpressionStructure() {
        // Arrange.
        val reasoner = traced()

        // Act.
        reasoner.evalWithTrace(expression("X.weight < 2"))
        val root = reasoner.expressionTrace.single()

        // Assert.
        assertTrue(root.expression is CompareWithComparisonOperator)
        assertEquals(true, root.value)
        assertTrue(root.isValueAnnotated)
        val (property, literal) = root.children
        assertTrue(property.expression is GetPropertyValue)
        assertEquals(1, property.value)
        assertEquals(Obj("a"), property.children.single().value)
        assertTrue(property.children.single().isValueAnnotated)
        assertTrue(literal.expression is IntegerLiteral)
        assertNull(literal.value)
        assertTrue(!literal.isValueAnnotated)
    }

    /** Каждое вычисление верхнего уровня добавляет отдельный корень трассы. */
    @Test
    fun eachTopLevelEvaluationAddsRoot() {
        // Arrange.
        val reasoner = traced()

        // Act.
        reasoner.evalWithTrace(expression("1"))
        reasoner.evalWithTrace(expression("2"))

        // Assert.
        assertEquals(2, reasoner.expressionTrace.size)
    }

    /** Тело квантора трассируется для каждого просмотренного объекта, перебор прерывается на первом подходящем. */
    @Test
    fun quantifierBodyIsTracedPerObject() {
        // Arrange.
        val reasoner = traced()

        // Act.
        reasoner.evalWithTrace(expression($$"forAny item i { $i.weight == 3 }"))
        val root = reasoner.expressionTrace.single()

        // Assert.
        assertTrue(root.expression is ExistenceQuantifier)
        assertEquals(true, root.value)
        assertEquals(listOf(false, false, true), root.children.map { it.value })
    }

    /** Селектор квантора отражается в трассе: неподошедшие объекты - короткими записями, подошедшие - полной трассой условия. */
    @Test
    fun selectorIterationsAreTraced() {
        // Arrange.
        val reasoner = traced()

        // Act.
        reasoner.evalWithTrace(expression($$"forAll item i [ $i.sold ] { $i.weight > 0 }"))
        val root = reasoner.expressionTrace.single()
        val iterations = root.children.filter { it.iterationObject != null }

        // Assert.
        assertEquals(listOf(Obj("a"), Obj("b"), Obj("c"), Obj("d"), Obj("e")), iterations.map { it.iterationObject })
        assertEquals(listOf(false, true, false, true, false), iterations.map { it.value })
        assertEquals(emptyList(), iterations[0].children)
        assertTrue(iterations[1].children.isNotEmpty())
    }

    /** Записи о неподошедших объектах селектора ограничены 32 на уровень. */
    @Test
    fun rejectedSelectorIterationsAreCapped() {
        // Arrange.
        val objects = (1..40).joinToString("\n") { "obj n$it : node { flag = false ; }" }
        val reasoner = DomainInterpreterReasoner(ReasonerFixtures.situation(ReasonerFixtures.nodes(objects)), collectExpressionTrace = true)

        // Act.
        reasoner.evalWithTrace(expression($$"forAny node n [ $n.flag ] { true }"))
        val root = reasoner.expressionTrace.single()

        // Assert.
        assertEquals(false, root.value)
        assertEquals(32, root.children.count { it.iterationObject != null })
    }

    /** Ошибка вычисления несёт собранную к этому моменту трассу. */
    @Test
    fun errorCarriesCollectedTrace() {
        // Arrange.
        val reasoner = traced()

        // Act.
        val error = assertFailsWith<ReasoningException> { reasoner.evalWithTrace(expression("obj:a.weight < \"x\"")) }

        // Assert.
        assertNotNull(error.expressionTrace)
        assertTrue(error.expressionTrace!!.single().expression is CompareWithComparisonOperator)
    }

    /** Переменная контекста аннотируется своим значением. */
    @Test
    fun contextVariableIsAnnotated() {
        // Arrange.
        val reasoner = DomainInterpreterReasoner(situation, mapOf("n" to Obj("c")), collectExpressionTrace = true)

        // Act.
        reasoner.evalWithTrace(expression($$"$n"))
        val root = reasoner.expressionTrace.single()

        // Assert.
        assertTrue(root.expression is VariableLiteral)
        assertEquals(Obj("c"), root.value)
        assertTrue(root.isValueAnnotated)
    }

    /** Сбор трассы не меняет результат вычисления. */
    @Test
    fun traceCollectionDoesNotChangeResult() {
        // Arrange.
        val search = expression($$"findExtreme far [ $far=>leadsTo($i) ] among item i { forAny item j { $i=>leadsTo($j) } }")

        // Act.
        val plain = DomainInterpreterReasoner(situation).evalWithTrace(search)
        val withTrace = traced().evalWithTrace(search)

        // Assert.
        assertEquals(Obj("a"), plain)
        assertEquals(plain, withTrace)
    }
}
