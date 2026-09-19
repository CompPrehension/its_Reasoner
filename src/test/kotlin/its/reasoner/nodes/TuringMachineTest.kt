package its.reasoner.nodes

import its.model.definition.types.EnumValue
import its.model.definition.types.Obj
import its.model.nodes.BranchResult
import its.reasoner.LearningSituation
import its.reasoner.ReasonerFixtures
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.operators.DomainInterpreterReasoner
import its.reasoner.operators.OperatorReasoner.Companion.evalAs
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Интерпретатор машины Тьюринга как граф мыслительных процессов.
 *
 * Сама машина (алфавит, состояния, таблица переходов, лента) задаётся предметной моделью.
 * Лента не ограничена: при выходе головки за край граф достраивает новые пустые ячейки.
 */
class TuringMachineTest {

    private companion object {

        /**
         * Классы машины Тьюринга:
         * - `cell` - ячейка ленты с символом и линейным порядком `right` (`left` - обратное);
         * - `state` - состояние с именем, флагами принимающего/отвергающего и переходами
         *   `hasTransition<symbol, direction, write>(state)`: по прочитанному символу `symbol`
         *   записать `write`, сдвинуться в `direction` и перейти в целевое состояние.
         */
        const val CLASSES = """
            enum Symbol { blank, zero, one }
            enum Direction { left, right, stay }

            class cell {
                obj prop symbol: Symbol ;
                rel right(cell) : linear ;
                rel left(cell) : opposite to right ;
            }

            class state {
                obj prop name: string ;
                obj prop isAccepting: bool ;
                obj prop isRejecting: bool ;
                rel hasTransition<symbol: Symbol, direction: Direction, write: Symbol>(state) ;
            }
        """

        /**
         * Программа "бинарный инкремент" (старший разряд слева, головка на старшем разряде):
         * - `scan` идёт вправо до конца числа;
         * - `carry` идёт влево, заменяя 1 на 0, пока не встретит 0 или пустую ячейку - туда пишет 1 и принимает.
         */
        const val INCREMENT_PROGRAM = """
            obj scan : state {
                name = "scan" ; isAccepting = false ; isRejecting = false ;
                hasTransition<Symbol:zero, Direction:right, Symbol:zero>(scan) ;
                hasTransition<Symbol:one, Direction:right, Symbol:one>(scan) ;
                hasTransition<Symbol:blank, Direction:left, Symbol:blank>(carry) ;
            }
            obj carry : state {
                name = "carry" ; isAccepting = false ; isRejecting = false ;
                hasTransition<Symbol:one, Direction:left, Symbol:zero>(carry) ;
                hasTransition<Symbol:zero, Direction:stay, Symbol:one>(accept) ;
                hasTransition<Symbol:blank, Direction:stay, Symbol:one>(accept) ;
            }
            obj accept : state { name = "accept" ; isAccepting = true ; isRejecting = false ; }
        """

        /** Универсальный интерпретатор: один шаг машины за одну итерацию цикла. */
        val INTERPRETER = ReasonerFixtures.tree($$"""
            tpg TuringMachine(Head: cell, State: state) {
                while (not State.isAccepting and not State.isRejecting) {
                    _ -> {
                        var Next: state = find state t { State=>hasTransition<symbol = Head.symbol>($t) }
                            out true else { conclude: error };
                        ask switch (State=>hasTransition<symbol = Head.symbol>(Next).direction) {
                            Direction:stay -> {
                                eval(Head.symbol = State=>hasTransition<symbol = Head.symbol>(Next).write);
                                conclude: null with (State = Next)
                            };
                            Direction:right -> {
                                eval({
                                    if (not Head=>right()) Head +=> right(+obj:cell({ symbol = Symbol:blank ; })) ;
                                    Head.symbol = State=>hasTransition<symbol = Head.symbol>(Next).write
                                });
                                conclude: null with ({ Head = Head->right ; State = Next })
                            };
                            Direction:left -> {
                                eval({
                                    if (not Head=>left()) +obj:cell({ symbol = Symbol:blank ; right(Head) ; }) ;
                                    Head.symbol = State=>hasTransition<symbol = Head.symbol>(Next).write
                                });
                                conclude: null with ({ Head = Head->left ; State = Next })
                            };
                        }
                    };
                    error -> { conclude: error };
                    null -> { ask (State.isAccepting) { true -> { conclude: correct }; false -> { conclude: error } } };
                }
            }
        """)

        fun symbolName(digit: Char) = when (digit) {
            '0' -> "zero"
            '1' -> "one"
            else -> "blank"
        }

        fun digit(symbol: EnumValue) = when (symbol.valueName) {
            "zero" -> '0'
            "one" -> '1'
            else -> '_'
        }

        /** Лента из строки: ячейки c0, c1, ..., связанные слева направо. */
        fun tape(digits: String): String {
            return digits.indices.joinToString("\n") { i ->
                val next = if (i + 1 < digits.length) "right(c${i + 1}) ;" else ""
                "obj c$i : cell { symbol = Symbol:${symbolName(digits[i])} ; $next }"
            }
        }

        /** Ситуация: программа инкремента, лента с данным словом, головка на первой ячейке, стартовое состояние `scan`. */
        fun situation(input: String): LearningSituation {
            val domain = ReasonerFixtures.domain(CLASSES + INCREMENT_PROGRAM + tape(input))
            return ReasonerFixtures.situation(domain, "Head" to "c0", "State" to "scan")
        }

        /** Содержимое ленты: обход от самой левой ячейки по `right`. */
        fun readTape(situation: LearningSituation): String {
            val reasoner = DomainInterpreterReasoner(situation)
            fun eval(loqi: String) = ReasonerFixtures.expression(loqi).evalAs<Any?>(reasoner)

            val result = StringBuilder()
            var cell = eval($$"find cell c { not $c=>left() }") as Obj
            while (true) {
                result.append(digit(eval("obj:${cell.objectName}.symbol") as EnumValue))
                if (eval("obj:${cell.objectName}=>right()") != true) break
                cell = eval("obj:${cell.objectName}->right") as Obj
            }
            return result.toString()
        }

        /** Прогон машины на слове: результат ветви и лента после останова. */
        fun run(input: String): Pair<BranchResult, String> {
            val situation = situation(input)
            val trace = INTERPRETER.solve(situation)
            return trace.branchResult to readTape(situation)
        }
    }

    /** 1011 + 1 = 1100; справа достроена пустая ячейка, на которую заходила головка. */
    @Test
    fun incrementWithoutCarryOverflow() {
        assertEquals(BranchResult.CORRECT to "1100_", run("1011"))
    }

    /** 111 + 1 = 1000: перенос выходит за левый край, лента достраивается слева. */
    @Test
    fun incrementWithCarryOverflow() {
        assertEquals(BranchResult.CORRECT to "1000_", run("111"))
    }

    /** 0111 + 1 = 1000: перенос влияет на число с нулем в начале перезаписывая его его на 1. */
    @Test
    fun incrementWithCarryOverflowAndLeadingZero() {
        assertEquals(BranchResult.CORRECT to "1000_", run("0111"))
    }

    /** 00111 + 1 = 01000: перенос влияет на число с нулями в начале перезаписывая самый ближний на 1. */
    @Test
    fun incrementWithCarryOverflowAndLeadingZeroы() {
        assertEquals(BranchResult.CORRECT to "01000_", run("00111"))
    }

    /** 0 + 1 = 1. */
    @Test
    fun incrementZero() {
        assertEquals(BranchResult.CORRECT to "1_", run("0"))
    }

    /** Головка начинает на пустой ячейке: слева достраивается ячейка и в неё пишется 1. */
    @Test
    fun incrementEmptyTape() {
        assertEquals(BranchResult.CORRECT to "1_", run("_"))
    }

    /** Каждый шаг машины - одна итерация цикла: 5 шагов scan (4 разряда + пустая ячейка) и 3 шага carry. */
    @Test
    fun eachMachineStepIsOneCycleIteration() {
        // Arrange.
        val situation = situation("1011")

        // Act.
        val trace = INTERPRETER.solve(situation)

        // Assert.
        val cycle = trace.first() as WhileCycleDecisionTreeTraceElement
        assertEquals(8, cycle.branchTraceList.size)
        assertEquals(Obj("accept"), situation.decisionTreeVariables["State"])
        assertEquals(Obj("c1"), situation.decisionTreeVariables["Head"], "головка остаётся на разряде, куда записана 1")
    }
}
