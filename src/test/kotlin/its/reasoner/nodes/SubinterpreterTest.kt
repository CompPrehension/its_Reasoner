package its.reasoner.nodes

import its.model.DomainSolvingModel
import its.model.nodes.BranchResult
import its.model.nodes.QuestionNode
import its.reasoner.LearningSituation
import its.reasoner.ReasonerFixtures
import its.reasoner.ReasoningMisuseException
import its.reasoner.SubinterpreterException
import its.reasoner.procedures.callSubinterpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Субинтерпретаторы: вложенное прорешивание другого дерева на копии модели.
 */
class SubinterpreterTest : DecisionTreeTestBase() {

    private val trees = mapOf(
        "isSold" to tree("""
            tpg IsSold(V: item) {
                ask (V.sold) { true -> { conclude: correct }; false -> { conclude: error } }
            }
        """),
        "markSold" to tree("""
            tpg MarkSold(V: item) {
                var Found: item = V;
                conclude: correct with (V.sold = true)
            }
        """),
        "explode" to tree("""
            tpg Explode(V: item) {
                conclude: error [ exception = "true" ; exceptionName = "Boom" ; id = "boom" ]
            }
        """),
        "nothing" to tree("tpg Nothing(V: item) { conclude: null }"),
        "explodeAfterAggregation" to tree("""
            tpg ExplodeAfterAggregation(V: item) {
                agg and {
                    _ -> { conclude: error };
                    error -> { conclude: error [ exception = "true" ; exceptionName = "Aggregated" ] };
                }
            }
        """),
    )

    private fun situationWithTrees(vararg variables: Pair<String, String>): LearningSituation {
        val domain = domain()
        val solvingModel = DomainSolvingModel(domain, emptyMap(), trees)
        return LearningSituation(domain, ReasonerFixtures.situation(domain, *variables).decisionTreeVariables, solvingModel)
    }

    private fun solveWithTrees(treeLoqi: String, vararg variables: Pair<String, String>): Solved {
        return solve(tree(treeLoqi), situationWithTrees(*variables))
    }

    /** Вызов субинтерпретатора в выражении возвращает результат дерева как опциональное логическое значение. */
    @Test
    fun subcallExpressionReturnsBranchResult() {
        // Arrange.
        val tree = """
            tpg T(X: item) {
                ask (subcall("isSold", X) == true) { true -> { conclude: correct }; false -> { conclude: error } }
            }
        """

        // Act & Assert.
        assertEquals(BranchResult.CORRECT, solveWithTrees(tree, "X" to "b").result)
        assertEquals(BranchResult.ERROR, solveWithTrees(tree, "X" to "a").result)
    }

    /** Результат null вложенного дерева отличим и от true, и от false. */
    @Test
    fun subcallNullResultIsNeitherTrueNorFalse() {
        // Act.
        val solved = solveWithTrees("""
            tpg T(X: item) {
                ask (subcall("nothing", X) == true or subcall("nothing", X) == false) { true -> { conclude: error }; false -> { conclude: correct } }
            }
        """, "X" to "a")

        // Assert.
        assertEquals(BranchResult.CORRECT, solved.result)
    }

    /** Обычный вызов работает на копии модели: изменения и переменные вложенного дерева не видны снаружи. */
    @Test
    fun subcallDoesNotAffectOuterSituation() {
        // Act.
        val solved = solveWithTrees("""
            tpg T(X: item) {
                subcall("markSold", X);
                conclude: correct
            }
        """, "X" to "a")

        // Assert.
        assertEquals(BranchResult.CORRECT, solved.result)
        assertEquals(false, solved.model.objects.get("a")!!.getPropertyValue("sold"))
        assertEquals(setOf("X"), solved.variables.keys)
    }

    /** Изменяющий вызов переносит изменения модели наружу; переменные - только по запросу. */
    @Test
    fun mutableSubcallAppliesChanges() {
        // Act.
        val withoutVariables = solveWithTrees("""
            tpg T(X: item) {
                subcall_mut("markSold", false, X);
                conclude: correct
            }
        """, "X" to "a")
        val withVariables = solveWithTrees("""
            tpg T(X: item) {
                subcall_mut("markSold", true, X);
                conclude: correct
            }
        """, "X" to "a")

        // Assert.
        assertEquals(true, withoutVariables.model.objects.get("a")!!.getPropertyValue("sold"))
        assertEquals(setOf("X"), withoutVariables.variables.keys)
        assertEquals(true, withVariables.model.objects.get("a")!!.getPropertyValue("sold"))
        assertEquals(mapOf("X" to obj("a"), "V" to obj("a"), "Found" to obj("a")), withVariables.variables)
    }

    /** Перенаправление результата: результат вложенного дерева становится результатом ветви, его трасса вкладывается. */
    @Test
    fun redirectedConclusionTakesNestedResult() {
        // Act.
        val solved = solveWithTrees("tpg T(X: item) { conclude: subcall(\"isSold\", X) }", "X" to "a")
        val element = solved.trace.last() as RedirectedBranchResultDecisionTreeTraceElement

        // Assert.
        assertEquals(BranchResult.ERROR, solved.result)
        assertEquals(1, element.subinterpreterTrace.count { it.node is QuestionNode })
        assertEquals(1, element.nestedTraces()!!.size)
        assertTrue(solved.trace.containsWithNested(element.subinterpreterTrace.first().node))
    }

    /** Перенаправление с действием выполняет действие на внешней модели. */
    @Test
    fun redirectedConclusionRunsAction() {
        // Act.
        val solved = solveWithTrees("tpg T(X: item) { conclude: subcall(\"isSold\", X) with (X.sold = true) }", "X" to "a")

        // Assert.
        assertEquals(BranchResult.ERROR, solved.result)
        assertEquals(true, solved.model.objects.get("a")!!.getPropertyValue("sold"))
    }

    /** Вложенное дерево, завершившееся исключением, прерывает внешнее решение специальным исключением. */
    @Test
    fun nestedExceptionPropagates() {
        // Act.
        val error = assertFailsWith<SubinterpreterException> {
            solveWithTrees("tpg T(X: item) { subcall(\"explode\", X); conclude: correct }", "X" to "a")
        }

        // Assert.
        assertEquals("explode", error.subinterpreterTreeName)
        assertEquals(BranchResult.ERROR, error.subinterpreterTrace.branchResult)
        assertTrue(error.message!!.contains("Boom"))
    }

    /** Исключение после агрегации с тем же результатом тоже прерывает внешнее решение. */
    @Test
    fun nestedExceptionAfterAggregationPropagates() {
        // Act.
        val error = assertFailsWith<SubinterpreterException> {
            solveWithTrees("tpg T(X: item) { subcall(\"explodeAfterAggregation\", X); conclude: correct }", "X" to "a")
        }

        // Assert.
        assertTrue(error.message!!.contains("Aggregated"))
    }

    /** Вызов без контекста решения невозможен. */
    @Test
    fun subcallRequiresSolvingContext() {
        assertFailsWith<Throwable> { solve("tpg T(X: item) { subcall(\"isSold\", X); conclude: correct }", "X" to "a") }
    }

    /** Аргументы вложенного дерева проверяются: их должно хватать, и это должны быть объекты. */
    @Test
    fun subcallArgumentsAreChecked() {
        assertFailsWith<ReasoningMisuseException> { solveWithTrees("tpg T(X: item) { subcall(\"isSold\"); conclude: correct }", "X" to "a") }
        assertFailsWith<ReasoningMisuseException> { solveWithTrees("tpg T(X: item) { subcall(\"isSold\", 1); conclude: correct }", "X" to "a") }
    }

    /** Вызов неизвестного дерева - ошибка. */
    @Test
    fun unknownTreeFails() {
        assertFailsWith<Throwable> { solveWithTrees("tpg T(X: item) { subcall(\"missing\", X); conclude: correct }", "X" to "a") }
    }

    /** Прямой вызов субинтерпретатора возвращает трассу вложенного дерева, не трогая внешнюю ситуацию. */
    @Test
    fun directCallReturnsNestedTrace() {
        // Arrange.
        val situation = situationWithTrees("X" to "a")

        // Act.
        val trace = callSubinterpreter("markSold", situation, listOf(obj("a")))

        // Assert.
        assertEquals(BranchResult.CORRECT, trace.branchResult)
        assertEquals(mapOf("V" to obj("a"), "Found" to obj("a")), trace.finalVariableSnapshot)
        assertFalse(situation.domainModel.objects.get("a")!!.getPropertyValue("sold") as Boolean)
    }
}
