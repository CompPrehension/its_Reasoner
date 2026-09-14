package its.reasoner.nodes

import its.model.nodes.BranchResult
import its.model.nodes.BranchResultNode
import its.model.nodes.FindActionNode
import its.model.nodes.QuestionNode
import its.reasoner.ReasonerFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecisionTreeSolvingTest : DecisionTreeTestBase() {

    /** Дерево из одного вывода даёт трассу из одного результирующего элемента. */
    @Test
    fun singleConclusion() {
        // Act.
        val solved = solve("tpg T(X: item) { conclude: correct }", "X" to "a")

        // Assert.
        assertEquals(BranchResult.CORRECT, solved.result)
        assertEquals(1, solved.trace.size)
        assertTrue(solved.trace.single() is BranchResultDecisionTreeTraceElement)
        assertEquals(mapOf("X" to obj("a")), solved.trace.finalVariableSnapshot)
    }

    /** Вопрос ведёт по ветви, соответствующей ответу. */
    @Test
    fun questionFollowsAnswerBranch() {
        // Arrange.
        val tree = """
            tpg T(X: item) {
                ask (X.sold) {
                    true -> { conclude: correct };
                    false -> { conclude: error };
                }
            }
        """

        // Act & Assert.
        assertEquals(BranchResult.CORRECT, solve(tree, "X" to "b").result)
        assertEquals(BranchResult.ERROR, solve(tree, "X" to "a").result)
    }

    /** Форма "out ... else": один ответ продолжает ветвь, другой уводит в подветвь. */
    @Test
    fun questionOutElseContinuesBranch() {
        // Arrange.
        val tree = """
            tpg T(X: item) {
                ask (X.weight > 2) out true else { conclude: error };
                ask (X.sold) out false else { conclude: null };
                conclude: correct
            }
        """

        // Act & Assert.
        assertEquals(BranchResult.ERROR, solve(tree, "X" to "a").result)
        assertEquals(BranchResult.NULL, solve(tree, "X" to "d").result)
        assertEquals(BranchResult.CORRECT, solve(tree, "X" to "c").result)
        assertEquals(listOf(QuestionNode::class, QuestionNode::class, BranchResultNode::class), solve(tree, "X" to "c").trace.map { it.node::class })
    }

    /** Вопрос с нелогическим ответом выбирает ветвь по значению. */
    @Test
    fun questionWithValueOutcomes() {
        // Arrange.
        val tree = """
            tpg T(X: item) {
                ask switch (X.color) {
                    Color:red -> { conclude: correct };
                    Color:green, Color:blue -> { conclude: error };
                }
            }
        """

        // Act & Assert.
        assertEquals(BranchResult.CORRECT, solve(tree, "X" to "a").result)
        assertEquals(BranchResult.ERROR, solve(tree, "X" to "b").result)
        assertEquals(BranchResult.ERROR, solve(tree, "X" to "c").result)
    }

    /** Ответ, для которого нет исхода, - ошибка прорешивания. */
    @Test
    fun answerWithoutOutcomeFails() {
        // Arrange.
        val tree = """
            tpg T(X: item) {
                ask switch (X.color) {
                    Color:red -> { conclude: correct };
                }
            }
        """

        // Act.
        val error = solveFails<IllegalArgumentException>(tree, "X" to "b")

        // Assert.
        assertTrue(error.message!!.contains("has no outcome with value"))
    }

    /** Найденный объект становится переменной дерева и виден дальше по ветви. */
    @Test
    fun findActionAssignsVariable() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                var Y: item = X->next;
                ask (Y.sold) out true else { conclude: error };
                conclude: correct
            }
        """, "X" to "a")

        // Assert.
        assertEquals(BranchResult.CORRECT, solved.result)
        assertEquals(obj("b"), solved.variables["Y"])
        assertEquals(mapOf("X" to obj("a"), "Y" to obj("b")), solved.trace.first().variablesSnapshot)
        assertEquals(true, (solved.trace.first() as LinkDecisionTreeTraceElement<*>).nodeResult)
    }

    /** Неудачный поиск уходит по ветви false, переменная не задаётся. */
    @Test
    fun failedFindActionFollowsFalseBranch() {
        // Act.
        val solved = solve($$"""
            tpg T(X: item) {
                var Y: item = find item i { $i.weight > 100 } out true else { conclude: null };
                conclude: correct
            }
        """, "X" to "a")

        // Assert.
        assertEquals(BranchResult.NULL, solved.result)
        assertEquals(false, solved.variables.containsKey("Y"))
        assertEquals(false, (solved.trace.first() as LinkDecisionTreeTraceElement<*>).nodeResult)
    }

    /** Дополнительные присваивания поиска вычисляются после основного. */
    @Test
    fun findActionSecondaryAssignments() {
        // Act.
        val solved = solve("""
            tpg T(X: item) {
                var Y: item with (Z: item = Y->next, W: box = Y->storedIn) = X->next;
                conclude: correct
            }
        """, "X" to "a")

        // Assert.
        assertEquals(obj("b"), solved.variables["Y"])
        assertEquals(obj("c"), solved.variables["Z"])
        assertEquals(obj("box1"), solved.variables["W"])
        assertTrue(solved.trace.first().node is FindActionNode)
    }

    /** Неявные переменные дерева вычисляются перед решением, если не заданы в ситуации. */
    @Test
    fun implicitVariablesAreComputed() {
        // Arrange.
        val tree = """
            tpg T(X: item, Y: item = X->next) {
                ask (Y.sold) out true else { conclude: error };
                conclude: correct
            }
        """

        // Act.
        val computed = solve(tree, "X" to "a")
        val given = solve(tree, "X" to "a", "Y" to "c")

        // Assert.
        assertEquals(obj("b"), computed.variables["Y"])
        assertEquals(BranchResult.CORRECT, computed.result)
        assertEquals(obj("c"), given.variables["Y"])
        assertEquals(BranchResult.ERROR, given.result)
    }

    /** Отсутствие обязательной переменной дерева - ошибка до начала решения. */
    @Test
    fun missingRequiredVariableFails() {
        // Act.
        val error = solveFails<IllegalArgumentException>("tpg T(X: item) { conclude: correct }")

        // Assert.
        assertTrue(error.message!!.contains("requires variable 'X'"))
    }

    /** Переменная неподходящего класса - ошибка до начала решения. */
    @Test
    fun variableOfWrongClassFails() {
        // Act.
        val error = solveFails<IllegalArgumentException>("tpg T(X: glass) { conclude: correct }", "X" to "a")

        // Assert.
        assertTrue(error.message!!.contains("expected to be of class 'glass'"))
    }

    /** Действие при выводе результата выполняется и меняет модель. */
    @Test
    fun conclusionActionIsExecuted() {
        // Act.
        val solved = solve("tpg T(X: item) { conclude: correct with (X.sold = true) }", "X" to "a")

        // Assert.
        assertEquals(BranchResult.CORRECT, solved.result)
        assertEquals(true, solved.model.objects.get("a")!!.getPropertyValue("sold"))
    }

    /** Переменные дерева, заданные в модели, подхватываются ситуацией. */
    @Test
    fun modelVariablesArePickedUpBySituation() {
        // Arrange.
        val model = ReasonerFixtures.domain(ReasonerFixtures.ITEMS_DOMAIN + "\nvar X = a\n")

        // Act.
        val solved = solve(tree("tpg T(X: item) { ask (X.weight == 1) { true -> { conclude: correct }; false -> { conclude: error } } }"), ReasonerFixtures.situation(model))

        // Assert.
        assertEquals(BranchResult.CORRECT, solved.result)
    }
}
