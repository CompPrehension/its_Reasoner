package its.reasoner.nodes

import its.model.nodes.BranchResult
import its.model.nodes.BranchResult.CORRECT
import its.model.nodes.BranchResult.ERROR
import its.model.nodes.BranchResult.NULL
import its.model.nodes.ThoughtBranch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AggregationNodesTest : DecisionTreeTestBase() {

    private fun conclusion(result: BranchResult) = "conclude: ${result.name.lowercase()}"

    private fun aggregation(method: String, vararg results: BranchResult): BranchResult {
        val branches = results.joinToString("\n") { "_ -> { ${conclusion(it)} };" }
        val solved = solve("""
            tpg T(X: item) {
                agg $method {
                    $branches
                    correct -> { conclude: correct };
                    error -> { conclude: error };
                    null -> { conclude: null };
                }
            }
        """, "X" to "a")
        assertEquals(results.toList(), (solved.trace.first() as AggregationDecisionTreeTraceElement<*>).branchTraceMap.values.map { it.branchResult })
        return solved.result
    }

    /** Агрегация AND: ошибка хоть в одной ветви - ошибка; иначе успех, если хоть одна ветвь дала результат. */
    @Test
    fun andAggregation() {
        assertEquals(CORRECT, aggregation("and", CORRECT, CORRECT))
        assertEquals(CORRECT, aggregation("and", CORRECT, NULL))
        assertEquals(ERROR, aggregation("and", CORRECT, ERROR))
        assertEquals(ERROR, aggregation("and", ERROR, NULL))
        assertEquals(NULL, aggregation("and", NULL, NULL))
    }

    /** Агрегация OR: успех хоть в одной ветви - успех; иначе ошибка, если хоть одна ветвь дала результат. */
    @Test
    fun orAggregation() {
        assertEquals(CORRECT, aggregation("or", CORRECT, ERROR))
        assertEquals(CORRECT, aggregation("or", NULL, CORRECT))
        assertEquals(ERROR, aggregation("or", ERROR, ERROR))
        assertEquals(ERROR, aggregation("or", ERROR, NULL))
        assertEquals(NULL, aggregation("or", NULL, NULL))
    }

    /** Агрегация HYP: успех, если есть успешная ветвь, иначе ошибка, если есть ошибочная, иначе null. */
    @Test
    fun hypAggregation() {
        assertEquals(CORRECT, aggregation("hyp", ERROR, CORRECT))
        assertEquals(ERROR, aggregation("hyp", ERROR, NULL))
        assertEquals(NULL, aggregation("hyp", NULL, NULL))
    }

    /** Агрегация MUTEX: результат единственной ветви с результатом; иначе null. */
    @Test
    fun mutexAggregation() {
        assertEquals(CORRECT, aggregation("mutex", CORRECT, NULL))
        assertEquals(ERROR, aggregation("mutex", NULL, ERROR))
        assertEquals(NULL, aggregation("mutex", CORRECT, ERROR))
        assertEquals(NULL, aggregation("mutex", CORRECT, CORRECT))
        assertEquals(NULL, aggregation("mutex", NULL, NULL))
    }

    /** Все ветви агрегации выполняются, даже если результат уже определён. */
    @Test
    fun allBranchesAreEvaluated() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                agg and {
                    _ -> { conclude: error with (obj:a.sold = true) };
                    _ -> { conclude: error with (obj:c.sold = true) };
                    error -> { conclude: error };
                }
            }
        """, "X" to "a")

        // Assert.
        assertEquals(ERROR, solved.result)
        assertEquals(true, solved.model.objects.get("a")!!.getPropertyValue("sold"))
        assertEquals(true, solved.model.objects.get("c")!!.getPropertyValue("sold"))
    }

    /** Исход агрегации без явной ветви продолжает основную ветвь. */
    @Test
    fun defaultOutcomeContinuesBranch() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                agg and {
                    _ -> { ask (X.weight > 0) out true else { conclude: error }; conclude: correct };
                    error -> { conclude: error };
                };
                conclude: null
            }
        """, "X" to "a")

        // Assert.
        assertEquals(NULL, solved.result)
        assertEquals(2, solved.trace.size)
        assertEquals(CORRECT, solved.trace.first().nodeResult)
    }

    /** Переменные, заданные внутри ветви агрегации, остаются в ситуации после неё. */
    @Test
    fun variablesAssignedInsideBranchesPersist() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                agg and {
                    _ -> { var Y: item = X->next; conclude: correct };
                    _ -> { var Z: item = X->next->next; conclude: correct };
                    correct -> { conclude: correct };
                }
            }
        """, "X" to "a")

        // Assert.
        assertEquals(obj("b"), solved.variables["Y"])
        assertEquals(obj("c"), solved.variables["Z"])
        val branchTraces = (solved.trace.first() as AggregationDecisionTreeTraceElement<ThoughtBranch>).branchTraceMap.values.toList()
        assertEquals(setOf("X", "Y"), branchTraces[0].finalVariableSnapshot.keys)
        assertEquals(setOf("X", "Y", "Z"), branchTraces[1].finalVariableSnapshot.keys)
    }

    /** Циклическая агрегация выполняет тело для каждого объекта селектора и агрегирует результаты. */
    @Test
    fun cycleAggregationIteratesSelectedObjects() {
        // Act.
        val solved = solve($$"""
            tpg T(X: item) {
                cycle and ($i=>follows(X)) with item i {
                    _ -> { ask (i.sold) { true -> { conclude: correct }; false -> { conclude: error } } };
                    correct -> { conclude: correct };
                    error -> { conclude: error };
                }
            }
        """, "X" to "b")
        val element = solved.trace.first() as AggregationDecisionTreeTraceElement<*>

        // Assert.
        assertEquals(ERROR, solved.result)
        assertEquals(listOf(obj("c"), obj("d")), element.branchTraceMap.keys.toList())
        assertEquals(listOf(ERROR, CORRECT), element.branchTraceMap.values.map { it.branchResult })
    }

    /** Переменная цикла видна только внутри тела и не остаётся в ситуации. */
    @Test
    fun cycleVariableIsRemovedAfterIteration() {
        // Act.
        val solved = solve($$"""
            tpg T(X: item) {
                cycle or ($i.weight > 3) with item i {
                    _ -> { conclude: correct };
                    correct -> { conclude: correct };
                }
            }
        """, "X" to "a")
        val element = solved.trace.first() as AggregationDecisionTreeTraceElement<*>

        // Assert.
        assertEquals(CORRECT, solved.result)
        assertEquals(setOf("X"), solved.variables.keys)
        assertTrue(element.branchTraceMap.values.all { it.finalVariableSnapshot["i"] != null })
        assertEquals(setOf("X"), element.variablesSnapshot.keys)
    }

    /** Цикл без объектов даёт null и уходит по ветви null. */
    @Test
    fun cycleWithoutObjectsGivesNull() {
        // Act.
        val solved = solve($$"""
            tpg T(X: item) {
                cycle and ($i.weight > 100) with item i {
                    _ -> { conclude: correct };
                    null -> { conclude: error };
                }
            }
        """, "X" to "a")

        // Assert.
        assertEquals(ERROR, solved.result)
        assertEquals(NULL, solved.trace.first().nodeResult)
    }

    /** Вложенная агрегация: результат внутренней становится результатом ветви внешней. */
    @Test
    fun nestedAggregations() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                agg or {
                    _ -> {
                        agg and {
                            _ -> { conclude: correct };
                            _ -> { conclude: error };
                            correct -> { conclude: correct };
                            error -> { conclude: error };
                        }
                    };
                    _ -> { conclude: null };
                    correct -> { conclude: correct };
                    error -> { conclude: error };
                }
            }
        """, "X" to "a")

        // Assert.
        assertEquals(ERROR, solved.result)
        assertTrue(solved.trace.first().isAggregated)
        assertEquals(2, solved.trace.first().nestedTraces()!!.size)
        assertTrue(solved.trace.first().nestedTraces()!!.first().first().isAggregated)
    }
}
