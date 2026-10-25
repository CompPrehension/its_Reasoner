package its.reasoner.operators

import its.model.definition.DomainModel
import its.model.definition.types.Obj
import its.model.expressions.Operator
import its.reasoner.LearningSituation
import its.reasoner.ReasonerFixtures
import its.reasoner.ReasonerFixtures.copy
import its.reasoner.ReasonerFixtures.describe
import its.reasoner.ReasonerFixtures.dump
import its.reasoner.ReasonerFixtures.expression
import its.reasoner.operators.OperatorReasoner.Companion.evalAs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Каждое вычисление выполняется дважды - с кэшем производных данных и без него -
 * на независимых копиях ситуации; результаты, исключения и итоговое состояние обязаны совпадать.
 */
abstract class OperatorTestBase {

    protected open fun domain(): DomainModel = ReasonerFixtures.items()

    protected open fun variables(): Map<String, String> = emptyMap()

    protected val situation: LearningSituation by lazy { ReasonerFixtures.situation(domain(), variables()) }

    protected val model: DomainModel
        get() = situation.domainModel

    protected fun eval(loqi: String, context: Map<String, Any> = emptyMap()): Any? {
        return eval(expression(loqi), context)
    }

    protected fun eval(expression: Operator, context: Map<String, Any> = emptyMap()): Any? {
        val reference = situation.copy()
        val expected = runCatching { evaluate(reference, expression, context, useCache = false) }
        val actual = runCatching { evaluate(situation, expression, context, useCache = true) }

        assertEquals(describe(expected), describe(actual), "результат без кэша и с кэшем")
        assertEquals(dump(reference.domainModel), dump(situation.domainModel), "состояние модели без кэша и с кэшем")
        assertEquals(reference.decisionTreeVariables, situation.decisionTreeVariables, "переменные дерева без кэша и с кэшем")
        return actual.getOrThrow()
    }

    protected fun evalOnce(loqi: String, context: Map<String, Any> = emptyMap()): Any? {
        return evaluate(situation, expression(loqi), context, useCache = true)
    }

    protected inline fun <reified E : Throwable> evalFails(loqi: String, context: Map<String, Any> = emptyMap()): E {
        return assertFailsWith<E> { eval(loqi, context) }
    }

    protected fun obj(name: String) = Obj(name)

    protected fun objects(vararg names: String) = names.map { Obj(it) }

    private fun evaluate(situation: LearningSituation, expression: Operator, context: Map<String, Any>, useCache: Boolean): Any? {
        val reasoner = DomainInterpreterReasoner(situation, context, useEvaluationCache = useCache)
        return expression.evalAs<Any?>(reasoner)
    }
}
