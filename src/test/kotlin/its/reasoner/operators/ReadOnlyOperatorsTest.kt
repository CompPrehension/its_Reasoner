package its.reasoner.operators

import its.model.definition.loqi.OperatorLoqiBuilder
import its.model.expressions.Operator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadOnlyOperatorsTest {

    /** Каждый оператор, который умеет вычислять ризонер, отнесён ровно к одной из групп. */
    @Test
    fun everyInterpretedOperatorIsClassified() {
        val interpreted = OperatorReasoner::class.java.methods
            .filter { it.name == "process" && it.parameterCount == 1 }
            .map { it.parameterTypes[0] }
            .filter { Operator::class.java.isAssignableFrom(it) && it != Operator::class.java }
            .toSet()

        val unclassified = interpreted - ReadOnlyOperators.READ_ONLY - ReadOnlyOperators.MUTATING
        assertEquals(emptySet(), unclassified, "Операторы без группы: ${unclassified.map { it.simpleName }}")
        assertEquals(emptySet(), ReadOnlyOperators.READ_ONLY intersect ReadOnlyOperators.MUTATING)
    }

    /** Выражение из читающих операторов не изменяет состояние. */
    @Test
    fun readingExpressionIsReadOnly() {
        val expression = OperatorLoqiBuilder.buildExp("forAny node n [\$n=>follows(X)] { \$n.flag and X->next.flag }")

        assertTrue(ReadOnlyOperators.isReadOnly(expression))
    }

    /** Изменяющий оператор на любой глубине делает всё выражение изменяющим. */
    @Test
    fun nestedMutationMakesExpressionMutating() {
        val expression = OperatorLoqiBuilder.buildExp("forAll node n { { \$n.flag = true; \$n.flag } }")

        assertFalse(ReadOnlyOperators.isReadOnly(expression))
    }
}
