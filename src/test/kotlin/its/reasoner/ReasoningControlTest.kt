package its.reasoner

import its.reasoner.ReasonerFixtures.expression
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.operators.DomainInterpreterReasoner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReasoningControlTest {

    private fun checkpoints(control: ReasoningControl, count: Int = 5000, location: Any? = null) {
        repeat(count) { control.checkpoint(location) }
    }

    private fun <T> interrupted(action: () -> T): T {
        Thread.currentThread().interrupt()
        try {
            return action()
        } finally {
            Thread.interrupted()
        }
    }

    /** Лимит времени должен быть положительным. */
    @Test
    fun timeLimitMustBePositive() {
        assertFailsWith<IllegalArgumentException> { ReasoningControl.withTimeLimitSeconds(0) }
        assertFailsWith<IllegalArgumentException> { ReasoningControl.withTimeLimitSeconds(-1) }
    }

    /** Отключённый контроль не реагирует ни на прерывание, ни на время. */
    @Test
    fun disabledControlNeverThrows() {
        interrupted { checkpoints(ReasoningControl.NONE) }
    }

    /** Прерывание потока обнаруживается контрольными точками. */
    @Test
    fun interruptionIsDetected() {
        // Arrange.
        val control = ReasoningControl.withTimeLimitSeconds(60)

        // Act & Assert.
        interrupted { assertFailsWith<ReasoningInterruptedException> { checkpoints(control) } }
    }

    /** Проверка выполняется не на каждой контрольной точке: единичный вызов прерывание не замечает. */
    @Test
    fun singleCheckpointDoesNotCheck() {
        // Arrange.
        val control = ReasoningControl.withTimeLimitSeconds(60)

        // Act & Assert.
        interrupted { control.checkpoint() }
    }

    /** Истёкший лимит времени обнаруживается с указанием места. */
    @Test
    fun expiredTimeLimitIsDetected() {
        // Arrange.
        val control = ReasoningControl.withTimeLimitSeconds(1)
        Thread.sleep(1100)

        // Act.
        val error = assertFailsWith<ReasoningTimeoutException> { checkpoints(control, location = "here") }

        // Assert.
        assertEquals(1, error.timeLimitSeconds)
        assertEquals("here", error.locationDescription)
        assertTrue(error.message!!.contains("exceeded at here"))
    }

    /** Прерывание останавливает вычисление выражения ризонером. */
    @Test
    fun interruptionStopsExpressionEvaluation() {
        // Arrange.
        val situation = ReasonerFixtures.situation(ReasonerFixtures.items())
        val reasoner = DomainInterpreterReasoner(situation, control = ReasoningControl.withTimeLimitSeconds(60))
        val heavy = expression($$"forAll item a { forAll item b { forAll item c { forAll item d { forAll item e { $a.weight >= 0 and $b.weight >= 0 and $c.weight >= 0 and $d.weight >= 0 and $e.weight >= 0 } } } } }")

        // Act & Assert.
        interrupted { assertFailsWith<ReasoningInterruptedException> { reasoner.evalWithTrace(heavy) } }
    }

    /** Прерывание останавливает решение дерева; с частичной трассой оно оборачивается в исключение ризонера. */
    @Test
    fun interruptionStopsTreeSolving() {
        // Arrange.
        val tree = ReasonerFixtures.tree($$"""
            tpg T(X: item) {
                ask (forAll item a { forAll item b { forAll item c { forAll item d { forAll item e { $a.weight >= 0 and $b.weight >= 0 and $c.weight >= 0 and $d.weight >= 0 and $e.weight >= 0 } } } } }) out true else { conclude: error };
                conclude: correct
            }
        """)
        val control = ReasoningControl.withTimeLimitSeconds(60)

        // Act.
        val plain = interrupted { assertFailsWith<ReasoningInterruptedException> { tree.solve(ReasonerFixtures.situation(ReasonerFixtures.items(), "X" to "a"), control) } }
        val wrapped = interrupted {
            assertFailsWith<ReasoningException> {
                tree.solve(ReasonerFixtures.situation(ReasonerFixtures.items(), "X" to "a"), ReasoningOptions(control = control, collectPartialTrace = true))
            }
        }

        // Assert.
        assertTrue(plain.message!!.contains("interrupted"))
        assertTrue(wrapped.cause is ReasoningInterruptedException)
    }
}
