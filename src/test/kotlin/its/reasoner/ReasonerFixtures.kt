package its.reasoner

import its.model.definition.DomainModel
import its.model.definition.ObjectDef
import its.model.definition.RelationshipDef
import its.model.definition.loqi.DomainLoqiBuilder
import its.model.definition.loqi.DomainLoqiWriter
import its.model.definition.loqi.OperatorLoqiBuilder
import its.model.definition.loqi.TreeLoqiBuilder
import its.model.definition.types.Obj
import its.model.expressions.Operator
import its.model.nodes.DecisionTree
import its.model.nodes.xml.DecisionTreeXMLBuilder
import its.reasoner.operators.RelationshipUtils
import java.io.StringReader
import java.io.StringWriter

object ReasonerFixtures {

    const val EXPRESSION_SITUATION_RESOURCE = "expression_situation.loqi"
    const val EXPRESSION_TREE_RESOURCE = "expression_tree.xml"

    /**
     * Предметы на полке: линейная шкала next с зависимыми отношениями, свойства всех типов,
     * свойство и отношение с параметрами, наследование с переопределением свойства класса,
     * коробки как проекция на предметы.
     */
    val ITEMS_CLASSES = """
        enum Color { red, green, blue }
        enum Lang { en, ru }

        class item {
            obj prop weight: int ;
            obj prop price: double ;
            obj prop color: Color ;
            obj prop sold: bool ;
            obj prop label<lang: Lang>: string ;
            class prop category: string = "generic" ;
            class prop fragile: bool = false ;
            rel next(item) : linear ;
            rel prev(item) : opposite to next ;
            rel leadsTo(item) : transitive to next ;
            rel follows(item) : opposite to leadsTo ;
            rel isBetween(item, item) : between to leadsTo ;
            rel isCloser(item, item) : closer to leadsTo ;
            rel isFurther(item, item) : further to leadsTo ;
            rel likes<strength: int>(item) ;
            rel storedIn(box) : opposite to box->holds ;
        }

        class glass : item {
            category = "glass" ;
            fragile = true ;
        }

        class box {
            obj prop capacity: int ;
            rel holds(item) : {1 -> *} ;
        }
    """

    val ITEMS_OBJECTS = """
        obj a : item { weight = 1 ; price = 1.5 ; color = Color:red ; sold = false ; label<Lang:en> = "a" ; label<Lang:ru> = "а" ; next(b) ; likes<3>(c) ; likes<1>(d) ; }
        obj b : glass { weight = 2 ; price = 2.5 ; color = Color:green ; sold = true ; label<Lang:en> = "b" ; next(c) ; }
        obj c : item { weight = 3 ; price = 3.5 ; color = Color:blue ; sold = false ; label<Lang:en> = "c" ; next(d) ; }
        obj d : glass { weight = 4 ; price = 4.5 ; color = Color:red ; sold = true ; label<Lang:en> = "d" ; }
        obj e : item { weight = 5 ; price = 5.5 ; color = Color:blue ; sold = false ; label<Lang:en> = "e" ; }
        obj box1 : box { capacity = 2 ; holds(a) ; holds(b) ; }
        obj box2 : box { capacity = 1 ; holds(d) ; }
    """

    val ITEMS_DOMAIN = ITEMS_CLASSES + ITEMS_OBJECTS

    /**
     * Минимальный класс для проверки шкал: объекты добавляются в каждом тесте отдельно
     */
    val NODE_CLASS = """
        class node {
            obj prop flag: bool ;
            rel next(node) : linear ;
            rel prev(node) : opposite to next ;
            rel leadsTo(node) : transitive to next ;
            rel follows(node) : opposite to leadsTo ;
            rel isBetween(node, node) : between to leadsTo ;
            rel isCloser(node, node) : closer to leadsTo ;
            rel isFurther(node, node) : further to leadsTo ;
        }
    """

    fun domain(loqi: String, validate: Boolean = true): DomainModel {
        val model = DomainLoqiBuilder.buildDomain(StringReader(loqi))
        if (validate) model.validateAndThrow()
        return model
    }

    fun items(): DomainModel = domain(ITEMS_DOMAIN)

    fun nodes(objects: String): DomainModel = domain(NODE_CLASS + objects)

    fun expression(loqi: String): Operator = OperatorLoqiBuilder.buildExp(loqi)

    fun tree(loqi: String): DecisionTree = TreeLoqiBuilder.buildTree(StringReader(loqi))

    fun situation(domain: DomainModel, variables: Map<String, String> = emptyMap()): LearningSituation {
        val treeVariables = LearningSituation.collectDecisionTreeVariables(domain)
        variables.forEach { (name, objectName) -> treeVariables[name] = Obj(objectName) }
        return LearningSituation(domain, treeVariables)
    }

    fun situation(domain: DomainModel, vararg variables: Pair<String, String>): LearningSituation {
        return situation(domain, variables.toMap())
    }

    fun LearningSituation.copy(): LearningSituation {
        return LearningSituation(domainModel.copy(), decisionTreeVariables.toMutableMap(), solvingContext)
    }

    fun dump(domain: DomainModel): String {
        return StringWriter().also { DomainLoqiWriter.saveDomain(domain, it) }.toString()
    }

    fun resourceText(name: String): String {
        return ReasonerFixtures::class.java.classLoader.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }
    }

    fun expressionSituation(): DomainModel = domain(resourceText(EXPRESSION_SITUATION_RESOURCE))

    fun expressionTree(): DecisionTree = DecisionTreeXMLBuilder.fromXMLString(resourceText(EXPRESSION_TREE_RESOURCE))

    fun DomainModel.relationship(className: String, relationshipName: String): RelationshipDef {
        return classes.get(className)!!.findRelationshipDef(relationshipName)!!
    }

    fun DomainModel.obj(name: String): ObjectDef = objects.get(name)!!

    fun describe(link: RelationshipUtils.RelationshipLinkView, relationship: RelationshipDef): String {
        return "${link.subj.name} ${link.relationshipName}${link.paramsValues.asMap(relationship.effectiveParams)}(${link.objects.map { it.name }})"
    }

    fun describe(outcome: Result<Any?>): String {
        return outcome.fold({ "result: $it" }, { "error: ${it.javaClass.name}: ${it.message}" })
    }

    fun <T> combinations(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return listOf(emptyList())
        val tails = combinations(lists.drop(1))
        return lists.first().flatMap { head -> tails.map { tail -> listOf(head) + tail } }
    }
}
