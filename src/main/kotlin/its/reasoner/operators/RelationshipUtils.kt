package its.reasoner.operators

import its.model.definition.*
import its.model.definition.BaseRelationshipKind.ScaleType
import its.reasoner.AmbiguousObjectException
import its.reasoner.ReasoningMisuseException

/**
 * Вспомогательный класс для вычисления связей по отношениям
 */
object RelationshipUtils {

    data class RelationshipLinkView(
        val subj: ObjectDef,
        val relationshipName: String,
        val objects: List<ObjectDef>,
        val paramsValues: ParamsValues = ParamsValues.EMPTY
    ) {
        constructor(actualLink: RelationshipLinkStatement) : this(
            actualLink.owner,
            actualLink.relationshipName,
            actualLink.objects,
            actualLink.paramsValues
        )
    }

    fun findSingleRelationshipLinkOrThrow(
        subj: ObjectDef,
        relationship: RelationshipDef,
        objects: List<ObjectDef>? = null,
        paramsValues: Map<String, Any> = mapOf(),
    ): RelationshipLinkView {
        val iterator = matchingRelationshipLinks(
            subj,
            relationship,
            objects,
            paramsValues
        ).iterator()
        if (!iterator.hasNext()) {
            throw AmbiguousObjectException("No links of $relationship found for $subj")
        }
        val link = iterator.next()
        if (iterator.hasNext()) {
            throw AmbiguousObjectException("Multiple links of $relationship found for $subj")
        }
        return link
    }

    fun findRelationshipLinks(
        subj: ObjectDef,
        relationship: RelationshipDef,
        objects: List<ObjectDef>? = null,
        paramsValues: Map<String, Any> = mapOf(),
    ): List<RelationshipLinkView> {
        return matchingRelationshipLinks(subj, relationship, objects, paramsValues).toList()
    }

    fun hasRelationshipLink(
        subj: ObjectDef,
        relationship: RelationshipDef,
        objects: List<ObjectDef>? = null,
        paramsValues: Map<String, Any> = mapOf(),
    ): Boolean {
        if (relationship.kind is BaseRelationshipKind) {
            return hasBaseRelationshipLink(subj, relationship, objects, paramsValues)
        }

        return matchingRelationshipLinks(subj, relationship, objects, paramsValues).any()
    }

    private fun matchingRelationshipLinks(
        subj: ObjectDef,
        relationship: RelationshipDef,
        objects: List<ObjectDef>? = null,
        paramsValues: Map<String, Any> = mapOf(),
    ): Sequence<RelationshipLinkView> {
        val relationshipLinkViews = when (relationship.kind) {
            is BaseRelationshipKind -> findBaseRelationshipLinks(subj, relationship, objects)
            is DependantRelationshipKind -> createDependentRelationshipLinks(subj, relationship, objects).asSequence()
        }

        if (paramsValues.isEmpty()) {
            return relationshipLinkViews
        }

        val effectiveParams = relationship.effectiveParams
        return relationshipLinkViews.filter {
            it.paramsValues.matchesPartial(paramsValues, effectiveParams)
        }
    }

    private fun createDependentRelationshipLinks(
        subj: ObjectDef,
        relationship: RelationshipDef,
        objects: List<ObjectDef>?,
    ): List<RelationshipLinkView> {
        val (rootRelationship, dependencySignature) = getCanonicalDependencySignature(relationship)

        if (!dependencySignature.needsScale && dependencySignature.isOpposite) {
            //Чисто "противоположные" бинарные связи
            return findBaseRelationshipLinks(objects?.get(0), rootRelationship, listOf(subj))
                .map { RelationshipLinkView(subj, relationship.name, listOf(it.subj), it.paramsValues) }
                .toList()
        }

        if (objects.isNullOrEmpty()) throw ReasoningMisuseException(
            "No relationship links for a dependent scalar relationship '${relationship.name}'" +
                    " can be found without specifying the objects"
        )

        //Для связей на шкале нужно построить цепочку связей, чтобы знать, где что находится
        val objectsInLineage = ArrayList<ObjectDef>(objects.size + 1)
        objectsInLineage.add(subj)
        objectsInLineage.addAll(objects)
        val lineage = getRelationshipLineage(rootRelationship, objectsInLineage)
        if (lineage == null) {
            //Если что-то пошло не так, то ничего не возвращаем
            return listOf()
        }
        val lineageIndexes = HashMap<ObjectDef, Int>(lineage.size)
        lineage.forEachIndexed { index, link ->
            lineageIndexes.putIfAbsent(link.obj, index)
        }
        if (objectsInLineage.any { it !in lineageIndexes }) {
            return listOf()
        }
        val indexes = objectsInLineage.associateWith { obj -> lineageIndexes.getValue(obj) }

        if (doesScalarLinkExist(subj, objects, dependencySignature, indexes)) {
            val indexList = objectsInLineage.map { indexes[it]!! }
            val linkIndex: Int
            if (dependencySignature.isOpposite && (rootRelationship.kind as BaseRelationshipKind).scaleType == ScaleType.Linear) {
                //Т.к. в линейных шкалах все симметрично, то для противоположных отношений берем минимальную связь
                linkIndex = indexList.min()
            } else {
                //Во всех остальных случаях берем максимальную связь, т.е. указывающую на самый верхний объект
                linkIndex = indexList.max() - 1 //вычитаем 1 из максимума, т.к. на него указывает предыдущая связь
            }

            val paramsValues = lineage[linkIndex].link!!.paramsValues
            return listOf(
                RelationshipLinkView(
                    subj,
                    relationship.name,
                    objects,
                    paramsValues
                )
            )
        }

        return listOf()
    }

    /**
     * Проверяем существование связи на шкале
     */
    private fun doesScalarLinkExist(
        subj: ObjectDef,
        objects: List<ObjectDef>,
        dependencySignature: DependencyTypeSignature,
        objectIndexesInLineage: Map<ObjectDef, Int>,
    ): Boolean {
        fun ObjectDef.precedesInLineage(other: ObjectDef): Boolean {
            return if (dependencySignature.isTransitive)
                objectIndexesInLineage[this]!! < objectIndexesInLineage[other]!!
            else
                objectIndexesInLineage[this]!! == objectIndexesInLineage[other]!! - 1
        }

        if (dependencySignature.isTernaryDependency) {
            //Тернарные зависимые отношения
            val (boundaryA, inner, boundaryB) =
                if (dependencySignature.isFurther)
                    listOf(objects[0], objects[1], subj)
                else
                    listOf(objects[0], subj, objects[1])

            return boundaryA.precedesInLineage(inner) && inner.precedesInLineage(boundaryB)
                    || boundaryB.precedesInLineage(inner) && inner.precedesInLineage(boundaryA)

        } else { //Транзитивные отношения (с opposite или без)
            return if (dependencySignature.isOpposite)
                objects[0].precedesInLineage(subj)
            else
                subj.precedesInLineage(objects[0])
        }
    }

    private fun findBaseRelationshipLinks(
        subj: ObjectDef?,
        relationship: RelationshipDef,
        objects: List<ObjectDef>?,
    ): Sequence<RelationshipLinkView> = sequence {
        val subjects = subj?.let { listOf(it) } ?: relationship.subjectClass.instances
        val objectNames = objects?.map { it.name }
        val unorderedObjectNames = if (!objectNames.isNullOrEmpty() && relationship.isUnordered) {
            objectNames.toHashSet()
        } else {
            emptySet()
        }
        for (subject in subjects) {
            for (link in subject.relationshipLinks) {
                if (link.matchesBaseRelationship(relationship, objectNames, unorderedObjectNames)) {
                    yield(RelationshipLinkView(link))
                }
            }
        }
    }

    private fun hasBaseRelationshipLink(
        subj: ObjectDef,
        relationship: RelationshipDef,
        objects: List<ObjectDef>?,
        paramsValues: Map<String, Any>,
    ): Boolean {
        val objectNames = objects?.map { it.name }
        val unorderedObjectNames = if (!objectNames.isNullOrEmpty() && relationship.isUnordered) {
            objectNames.toHashSet()
        } else {
            emptySet()
        }
        val effectiveParams = if (paramsValues.isEmpty()) null else relationship.effectiveParams

        for (link in subj.relationshipLinks) {
            if (link.matchesBaseRelationship(relationship, objectNames, unorderedObjectNames)
                && (effectiveParams == null || link.paramsValues.matchesPartial(paramsValues, effectiveParams))
            ) {
                return true
            }
        }

        return false
    }

    private fun RelationshipLinkStatement.matchesBaseRelationship(
        relationship: RelationshipDef,
        objectNames: List<String>?,
        unorderedObjectNames: Set<String>,
    ): Boolean {
        return relationshipName == relationship.name && (
                objectNames.isNullOrEmpty()
                        || this.objectNames == objectNames
                        || (unorderedObjectNames.isNotEmpty() && this.objectNames.hasSameElementsAsSet(unorderedObjectNames))
                )
    }

    private fun <T> List<T>.hasSameElementsAsSet(other: Set<T>): Boolean {
        val ownElements = HashSet<T>(size)
        for (element in this) {
            if (element !in other) {
                return false
            }
            ownElements.add(element)
        }
        return ownElements.size == other.size
    }


    /**
     * Возвращает т.н. "подпись зависимого отношения".
     * Она состоит из основного отношения (исходного, чей [RelationshipDef.kind] является [BaseRelationshipKind])
     * и списка модификаторов типа [DependantRelationshipKind.Type].
     */
    private fun getCanonicalDependencySignature(relationship: RelationshipDef): Pair<RelationshipDef, DependencyTypeSignature> {
        val signature = mutableSetOf<DependantRelationshipKind.Type>()
        var currentRelationship = relationship
        while (currentRelationship.kind is DependantRelationshipKind) {
            val type = (currentRelationship.kind as DependantRelationshipKind).type
            if (type == DependantRelationshipKind.Type.OPPOSITE
                && signature.contains(DependantRelationshipKind.Type.OPPOSITE)
            ) {
                signature.remove(DependantRelationshipKind.Type.OPPOSITE) //Два противоположных уничтожаются
            } else {
                signature.add(type)
            }
            currentRelationship = currentRelationship.baseRelationship!!
        }
        return currentRelationship to DependencyTypeSignature(signature)
    }

    class DependencyTypeSignature(private val dependencyTypes: Set<DependantRelationshipKind.Type>) {
        val isOpposite = dependencyTypes.contains(DependantRelationshipKind.Type.OPPOSITE)
        val isTransitive = dependencyTypes.contains(DependantRelationshipKind.Type.TRANSITIVE)
        val isCloser = dependencyTypes.contains(DependantRelationshipKind.Type.CLOSER)
        val isFurther = dependencyTypes.contains(DependantRelationshipKind.Type.FURTHER)
        val isBetween = dependencyTypes.contains(DependantRelationshipKind.Type.BETWEEN)
        val isTernaryDependency = isFurther || isCloser || isBetween
        val needsScale = isTransitive || isTernaryDependency
    }

    /**
     * Получить цепочку связей по шкале, в которой находятся все переданные объекты
     * возвращает null, если такую цепочку невозможно построить в текущих данных
     */
    private fun getRelationshipLineage(
        baseRelationship: RelationshipDef,
        objectsInLineage: List<ObjectDef>,
    ): List<RelationshipLineageLink>? {
        var lineage = listOf<RelationshipLineageLink>()
        val notFoundObjects = objectsInLineage.toMutableSet()

        //Нужно найти в цепочке все объекты
        while (notFoundObjects.isNotEmpty()) {
            val currentLineage = ArrayList<RelationshipLineageLink>(objectsInLineage.size + lineage.size)

            //Начиная с одного из еще не найденных объектов, идем вперед по цепочке
            var currentObject: ObjectDef? = notFoundObjects.first()
            while (currentObject != null) {
                val link = currentObject.relationshipLinks
                    .firstOrNull { it.relationshipName == baseRelationship.name }
                currentLineage.add(RelationshipLineageLink(currentObject, link))

                if (link == null) {
                    //Если впервые дошли до конца цепочки, то прерываемся
                    if (lineage.isEmpty()) break
                    //Иначе мы дошли до конца цепочки дважды, и значит либо объекты вообще не объединены в одну шкалу,
                    // либо находятся на разных ветвях частичной шкалы,
                    // но в любом случае они не связаны никаким отношением, поэтому возвращаем null как признак этого
                    else return null
                }

                //Переходим к следующему объекту
                currentObject = link.objects.firstOrNull()

                //Если дошли до уже найденного куска цепочки, то тоже прерываемся - нет смысла обходить его заново
                if (lineage.isNotEmpty() && currentObject == lineage[0].obj) break
            }

            currentLineage.forEach { notFoundObjects.remove(it.obj) }

            //Текущий кусок цепочки может быть только позади уже найденного, сливаем их в этом порядке
            currentLineage.addAll(lineage)
            lineage = currentLineage
        }
        return lineage
    }

    private data class RelationshipLineageLink(
        val obj: ObjectDef,
        val link: RelationshipLinkStatement?,
    )
}
