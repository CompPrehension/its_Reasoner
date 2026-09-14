package its.reasoner.utils

import its.model.definition.types.Obj
import its.reasoner.ReasonerFixtures
import its.reasoner.ReasonerFixtures.expression
import its.reasoner.ReasoningException
import its.reasoner.ReasoningOptions
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.operators.DomainInterpreterReasoner
import its.reasoner.operators.ExpressionQueryManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JSON-представление объектов и трасс: структура проверяется после записи и обратного разбора.
 */
class TraceJsonSerializationTest {

    private val model = ReasonerFixtures.domain(ReasonerFixtures.ITEMS_DOMAIN + "\nmeta for a [ RU.name = \"А\" ; kind = 1 ]\n")

    private fun situation(vararg variables: Pair<String, String>) = ReasonerFixtures.situation(model, *variables)

    private fun roundTrip(value: Any?): Any? = JsonParsing.parse(toJson(value))

    @Suppress("UNCHECKED_CAST")
    private fun Any?.map() = this as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Any?.list() = this as List<Any?>

    /** Известный объект описывается именем, классом и метаданными с кодом локализации. */
    @Test
    fun knownObjectIsSerializedWithTypeAndMetadata() {
        // Act.
        val json = roundTrip(Obj("a").toJsonValue(model)).map()

        // Assert.
        assertEquals("object a", json["repr_name"])
        assertEquals("a", json["object_name"])
        assertEquals("item", json["type"])
        assertEquals(
            setOf(mapOf("name" to "name", "locCode" to "RU", "value" to "А"), mapOf("name" to "kind", "locCode" to null, "value" to 1.0)),
            json["metadata"].list().toSet()
        )
    }

    /** Неизвестный объект описывается без класса и метаданных. */
    @Test
    fun unknownObjectIsSerializedWithoutType() {
        // Act.
        val json = roundTrip(Obj("ghost").toJsonValue(model)).map()

        // Assert.
        assertEquals("ghost", json["object_name"])
        assertNull(json["type"])
        assertEquals(emptyList<Any>(), json["metadata"])
    }

    /** Трасса дерева: результат, отсортированные переменные и элементы с типом узла, результатом и метаданными. */
    @Test
    fun decisionTreeTraceIsSerialized() {
        // Arrange.
        val tree = ReasonerFixtures.tree("""
            tpg T(X: item) {
                var Y: item = X->next;
                ask (Y.sold) out true else { conclude: error } as q;
                conclude: correct [ id = "done" ]
            }
            meta for q [ id = "q1" ; RU.label = "Продано?" ]
        """)
        val situation = situation("X" to "a")

        // Act.
        val json = roundTrip(tree.solve(situation).toJsonValue(verbose = false, situation.domainModel)).map()
        val elements = json["elements"].list().map { it.map() }

        // Assert.
        assertEquals("CORRECT", json["branchResult"])
        assertEquals(listOf("X", "Y"), json["finalVariables"].map().keys.toList())
        assertEquals("b", json["finalVariables"].map()["Y"].map()["object_name"])
        assertEquals(listOf("FindActionNode", "QuestionNode", "BranchResultNode"), elements.map { it["nodeType"] })
        assertEquals(listOf(null, "q1", "done"), elements.map { it["nodeId"] })
        assertEquals(listOf(true, true, "CORRECT"), elements.map { it["nodeResult"] })
        assertEquals(listOf("X", "Y"), elements[0]["variables"].map().keys.toList())
        assertTrue(elements[1]["metadata"].list().contains(mapOf("name" to "label", "locCode" to "RU", "value" to "Продано?")))
        assertTrue(elements.none { it.containsKey("node") })
    }

    /** Подробный режим добавляет текстовое описание узла. */
    @Test
    fun verboseTraceIncludesNodeDescription() {
        // Arrange.
        val situation = situation("X" to "a")

        // Act.
        val json = roundTrip(ReasonerFixtures.tree("tpg T(X: item) { conclude: correct }").solve(situation).toJsonValue(verbose = true, situation.domainModel)).map()

        // Assert.
        assertTrue(json["elements"].list().single().map()["node"] is String)
    }

    /** Агрегации, циклы и перенаправления вкладывают трассы ветвей. */
    @Test
    fun nestedTracesAreSerialized() {
        // Arrange.
        val tree = ReasonerFixtures.tree($$"""
            tpg T(X: item) {
                agg and {
                    _ -> { conclude: correct };
                };
                cycle or ($i.weight > 4) with item i {
                    _ -> { conclude: correct };
                    correct -> out;
                    error, null -> { conclude: error };
                };
                while (X=>next()) {
                    _ -> { conclude: null with (X = X->next) };
                    null -> { conclude: correct };
                }
            }
        """)
        val situation = situation("X" to "c")

        // Act.
        val elements = roundTrip(tree.solve(situation).toJsonValue(verbose = false, situation.domainModel)).map()["elements"].list().map { it.map() }
        val aggregation = elements[0]
        val cycle = elements[1]
        val loop = elements[2]

        // Assert.
        assertEquals("AND", aggregation["aggregationMethod"])
        assertEquals("ThoughtBranch", aggregation["branches"].list().single().map()["branch"])
        assertEquals("CORRECT", aggregation["branches"].list().single().map()["trace"].map()["branchResult"])
        assertEquals("e", cycle["branches"].list().single().map()["branch"].map()["object_name"])
        assertEquals(listOf(0.0), loop["iterations"].list().map { it.map()["index"] })
        assertEquals("NULL", loop["iterations"].list().single().map()["trace"].map()["branchResult"])
    }

    /** Частичная трасса описывает отказавший узел, переменные и выполненные шаги. */
    @Test
    fun partialTraceIsSerialized() {
        // Arrange.
        val tree = ReasonerFixtures.tree("""
            tpg T(X: item) {
                ask (X.weight > 0) out true else { conclude: null };
                ask (X->next.sold) out true else { conclude: error } as q;
                conclude: correct
            }
            meta for q [ id = "q2" ]
        """)
        val situation = situation("X" to "d")
        val error = assertFailsWith<ReasoningException> { tree.solve(situation, ReasoningOptions(collectPartialTrace = true)) }

        // Act.
        val json = roundTrip(error.partialDecisionTreeTrace!!.toJsonValue(verbose = false, situation.domainModel)).map()

        // Assert.
        assertEquals("QuestionNode", json["failedNode"].map()["nodeType"])
        assertEquals("q2", json["failedNode"].map()["nodeId"])
        assertEquals("d", json["variables"].map()["X"].map()["object_name"])
        assertEquals(1, json["elements"].list().size)
    }

    /** Трасса выражения: текст LOQI, аннотированные значения, дети и объекты итераций. */
    @Test
    fun expressionTraceIsSerialized() {
        // Arrange.
        val reasoner = DomainInterpreterReasoner(situation("X" to "a"), collectExpressionTrace = true)
        reasoner.evalWithTrace(expression($$"forAny item i [ $i.sold ] { $i == X->next }"))

        // Act.
        val root = roundTrip(reasoner.expressionTrace.toJsonValue(verbose = true, model)).list().single().map()
        val iterations = root["children"].list().map { it.map() }.filter { it.containsKey("iterationObject") }

        // Assert.
        assertEquals("forAny item i [\$i.sold] {\$i == X->next}", root["expression"])
        assertEquals(true, root["value"])
        assertEquals(true, root["isValueAnnotated"])
        assertEquals("ExistenceQuantifier", root["expressionType"])
        assertEquals(listOf("a", "b", "c", "d", "e"), iterations.map { it["iterationObject"].map()["object_name"] })
        assertEquals(listOf(false, true, false, true, false), iterations.map { it["value"] })
    }

    /** События CLI имеют ожидаемые типы и содержимое. */
    @Test
    fun cliEventsDescribeSolvingResult() {
        // Arrange.
        val tree = ReasonerFixtures.tree("tpg T(X: item) { conclude: error [ id = \"e1\" ; exception = \"true\" ; exceptionName = \"Boom\" ] }")
        val situation = situation("X" to "a")
        val trace = tree.solve(situation)

        // Act & Assert.
        assertEquals(mapOf("type" to "result", "name" to "branchResult", "value" to "ERROR"), roundTrip(resultEvent(trace)))
        assertEquals("BranchResultNode", roundTrip(finalNodeEvent(trace)).map()["nodeType"])
        assertEquals("final-node", roundTrip(finalNodeEvent(trace)).map()["type"])
        assertEquals("a", roundTrip(variablesEvent(trace, model)).map()["value"].map()["X"].map()["object_name"])
        assertEquals(
            mapOf("type" to "exceptions", "found" to true, "value" to listOf(mapOf("result" to "ERROR", "exceptionName" to "Boom", "id" to "e1"))),
            roundTrip(branchResultExceptionsEvent(trace))
        )
        assertEquals("trace", roundTrip(traceEvent(trace, false, model)).map()["type"])
        assertEquals(mapOf("type" to "metric", "name" to "solveTime", "seconds" to 1.5, "milliseconds" to 1500.0), roundTrip(metricEvent("solveTime", 1_500_000_000)))
        assertEquals(mapOf("type" to "reasoner-output", "level" to "debug", "value" to "hi"), roundTrip(reasonerOutputEvent("hi")))
    }

    /** Событие результата запроса перечисляет объекты и, по запросу, их LOQI. */
    @Test
    fun expressionQueryEventListsObjects() {
        // Arrange.
        val result = ExpressionQueryManager(situation()).query(expression($$"find item i { $i.sold }"))

        // Act.
        val plain = roundTrip(expressionQueryResultEvent(result)).map()
        val withLoqi = roundTrip(expressionQueryResultEvent(result, listOf("obj b", "obj d"))).map()

        // Assert.
        assertEquals(mapOf("type" to "expression-query-result", "objects" to listOf("b", "d")), plain)
        assertEquals(listOf(mapOf("name" to "b", "loqi" to "obj b"), mapOf("name" to "d", "loqi" to "obj d")), withLoqi["objectsLoqi"])
    }
}
