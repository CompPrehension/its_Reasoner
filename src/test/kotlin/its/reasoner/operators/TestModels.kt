package its.reasoner.operators

import its.model.definition.DomainModel
import its.model.definition.ObjectDef
import its.model.definition.RelationshipDef
import its.model.definition.loqi.DomainLoqiBuilder
import its.model.nodes.DecisionTree
import its.model.nodes.xml.DecisionTreeXMLBuilder
import java.io.StringReader

object TestModels {

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

    fun loqi(text: String, validate: Boolean = true): DomainModel {
        val model = DomainLoqiBuilder.buildDomain(StringReader(text))
        if (validate) model.validateAndThrow()
        return model
    }

    fun nodes(objects: String) = loqi(NODE_CLASS + objects)

    fun resourceText(name: String): String {
        return TestModels::class.java.classLoader.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }
    }

    fun expressionSituation(): DomainModel = loqi(resourceText("expression_situation.loqi"))

    fun expressionTree(): DecisionTree = DecisionTreeXMLBuilder.fromXMLString(resourceText("expression_tree.xml"))

    fun DomainModel.relationship(className: String, relationshipName: String): RelationshipDef {
        return classes.get(className)!!.findRelationshipDef(relationshipName)!!
    }

    fun DomainModel.obj(name: String): ObjectDef = objects.get(name)!!

    fun describe(link: RelationshipUtils.RelationshipLinkView, relationship: RelationshipDef): String {
        return "${link.subj.name} ${link.relationshipName}${link.paramsValues.asMap(relationship.effectiveParams)}(${link.objects.map { it.name }})"
    }

    fun <T> combinations(lists: List<List<T>>): List<List<T>> {
        if (lists.isEmpty()) return listOf(emptyList())
        val tails = combinations(lists.drop(1))
        return lists.first().flatMap { head -> tails.map { tail -> listOf(head) + tail } }
    }
}
