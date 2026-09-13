package its.reasoner.operators

import its.model.definition.BaseRelationshipKind
import its.model.definition.DomainModel
import its.model.definition.ObjectDef
import its.model.definition.RelationshipDef
import its.model.definition.RelationshipLinkStatement
import its.model.expressions.Operator
import its.model.expressions.literals.VariableLiteral
import java.util.IdentityHashMap

/**
 * Производные данные модели, накапливаемые на время вычисления одного выражения,
 * которое не изменяет состояние вычисления.
 */
class ReadOnlyEvaluationCache(private val domainModel: DomainModel) {

    /**
     * Линейная шкала - объекты, выстроенные в цепочки связями базового отношения.
     */
    class LinearScale internal constructor(
        private val chainIndexes: Map<ObjectDef, Int>,
        private val positions: Map<ObjectDef, Int>,
        private val chains: List<List<ObjectDef>>,
        private val forwardLinks: Map<ObjectDef, RelationshipLinkStatement>,
    ) {
        fun areOnSameChain(objects: List<ObjectDef>): Boolean {
            val chain = chainIndexes[objects.first()] ?: return false
            return objects.all { chainIndexes[it] == chain }
        }

        /**
         * Положение объекта в своей цепочке: чем больше, тем дальше по направлению связей
         */
        fun positionOf(obj: ObjectDef) = positions.getValue(obj)

        /**
         * Связь базового отношения, исходящая из объекта, стоящего на позиции [position] в цепочке объекта [obj]
         */
        fun linkFrom(obj: ObjectDef, position: Int): RelationshipLinkStatement? {
            return forwardLinks[chains[chainIndexes.getValue(obj)][position]]
        }
    }

    private val reverseLinks = HashMap<String, Map<String, List<RelationshipLinkStatement>>>()
    private val forwardLinks = HashMap<String, Map<ObjectDef, RelationshipLinkStatement?>>()
    private val linearScales = HashMap<String, LinearScale?>()
    private val memo = IdentityHashMap<Operator, HashMap<List<Any?>, Any?>>()
    private val variableNames = IdentityHashMap<Operator, List<String>>()

    //---Индексы связей---

    /**
     * Связи базового отношения [relationship], первым объектом которых является объект с именем [objectName]
     */
    fun linksPointingTo(relationship: RelationshipDef, objectName: String): List<RelationshipLinkStatement> {
        val index = reverseLinks.getOrPut(relationship.name) {
            val built = HashMap<String, MutableList<RelationshipLinkStatement>>()
            for (link in allLinks(relationship)) {
                val firstObjectName = link.objectNames.firstOrNull() ?: continue
                built.getOrPut(firstObjectName) { ArrayList(2) }.add(link)
            }
            built
        }
        return index[objectName] ?: emptyList()
    }

    /**
     * Первая связь базового отношения [relationship], исходящая из объекта [obj]
     */
    fun forwardLink(relationship: RelationshipDef, obj: ObjectDef): RelationshipLinkStatement? {
        val index = forwardLinks.getOrPut(relationship.name) {
            val built = HashMap<ObjectDef, RelationshipLinkStatement?>()
            for (link in allLinks(relationship)) {
                built.putIfAbsent(link.owner, link)
            }
            built
        }
        return index[obj]
    }

    /**
     * Линейная шкала базового отношения [relationship]
     * @return null, если отношение не является линейной шкалой либо его связи не образуют цепочек
     */
    fun linearScale(relationship: RelationshipDef): LinearScale? {
        return linearScales.getOrPut(relationship.name) { buildLinearScale(relationship) }
    }

    private fun buildLinearScale(relationship: RelationshipDef): LinearScale? {
        val kind = relationship.kind as? BaseRelationshipKind ?: return null
        if (kind.scaleType != BaseRelationshipKind.ScaleType.Linear) return null

        val forward = HashMap<ObjectDef, RelationshipLinkStatement>()
        val hasIncoming = HashSet<ObjectDef>()
        val subjects = subjectsOf(relationship)
        for (subject in subjects) {
            for (link in subject.relationshipLinks) {
                if (link.relationshipName != relationship.name) continue
                //Объект с несколькими исходящими связями не является частью линейной шкалы
                if (forward.putIfAbsent(subject, link) != null) return null
                val target = link.objects.firstOrNull() ?: return null
                if (!hasIncoming.add(target)) return null
            }
        }

        val chainIndexes = HashMap<ObjectDef, Int>()
        val positions = HashMap<ObjectDef, Int>()
        val chains = ArrayList<List<ObjectDef>>()
        for (subject in subjects) {
            if (subject in hasIncoming) continue
            val chain = ArrayList<ObjectDef>()
            var current: ObjectDef? = subject
            while (current != null) {
                if (positions.containsKey(current)) return null
                chainIndexes[current] = chains.size
                positions[current] = chain.size
                chain.add(current)
                current = forward[current]?.objects?.firstOrNull()
            }
            chains.add(chain)
        }
        //Объекты, не попавшие ни в одну цепочку, образуют цикл
        if (!subjects.all { it in positions }) return null

        return LinearScale(chainIndexes, positions, chains, forward)
    }

    private fun subjectsOf(relationship: RelationshipDef): List<ObjectDef> {
        return domainModel.objects.objectsAssignableTo(relationship.subjectClass.name)
    }

    private fun allLinks(relationship: RelationshipDef): Sequence<RelationshipLinkStatement> {
        return subjectsOf(relationship).asSequence()
            .flatMap { it.relationshipLinks.asSequence() }
            .filter { it.relationshipName == relationship.name }
    }

    //---Мемоизация подвыражений---

    /**
     * Вычислить подвыражение [operator] при текущих значениях переменных [varContext]
     * либо вернуть результат, полученный ранее для тех же значений.
     * Ключом служат значения всех переменных, упомянутых в подвыражении.
     */
    fun <T> memoize(operator: Operator, varContext: Map<String, Any>, compute: () -> T): T {
        val names = variableNames.getOrPut(operator) { collectVariableNames(operator) }
        val key = ArrayList<Any?>(names.size)
        for (name in names) key.add(varContext[name])

        val results = memo.getOrPut(operator) { HashMap() }
        if (results.containsKey(key)) {
            @Suppress("UNCHECKED_CAST")
            return results[key] as T
        }
        val value = compute()
        results[key] = value
        return value
    }

    private fun collectVariableNames(operator: Operator): List<String> {
        val names = LinkedHashSet<String>()
        fun visit(op: Operator) {
            if (op is VariableLiteral) names.add(op.name)
            op.children.forEach(::visit)
        }
        visit(operator)
        return names.toList()
    }
}
