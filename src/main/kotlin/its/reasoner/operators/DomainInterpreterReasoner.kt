package its.reasoner.operators

import its.model.Blueprint
import its.model.BlueprintContextProvider
import its.model.ObjectPropertyValueBlueprint
import its.model.RelationshipLinkBlueprint
import its.model.TypedVariable
import its.model.definition.*
import its.model.definition.types.Clazz
import its.model.definition.types.Comparison
import its.model.definition.types.EnumValue
import its.model.definition.types.ExpressionType
import its.model.definition.types.Obj
import its.model.expressions.Operator
import its.model.expressions.literals.Literal
import its.model.expressions.literals.*
import its.model.expressions.operators.*
import its.model.expressions.utils.ParamsValuesExprList
import its.reasoner.*
import its.reasoner.procedures.ProcedureImpl
import its.reasoner.utils.DomainUtils

/**
 * Ризонер для операторов на основе наивной интерпретации:
 * выполняет действия в операторах "как сказано" на основе информации в предметной области ([DomainModel])
 */
class DomainInterpreterReasoner private constructor(
    val situation: LearningSituation,
    val varContext: Map<String, Any>,
    private var blockPrevious: Any?,
    private val expressionTraceState: ExpressionTraceState,
    private val control: ReasoningControl,
) : OperatorReasoner {

    @JvmOverloads
    constructor(
        situation: LearningSituation,
        varContext: Map<String, Any> = mutableMapOf(),
        blockPrevious: Any? = null,
        collectExpressionTrace: Boolean = false,
        control: ReasoningControl = ReasoningControl.NONE,
    ) : this(situation, varContext, blockPrevious, ExpressionTraceState(collectExpressionTrace), control)

    private val domain
        get() = situation.domainModel

    val expressionTrace: List<ExpressionTrace>
        get() = expressionTraceState.trace

    private fun <T> Operator.evalAs(reasoner: OperatorReasoner = this@DomainInterpreterReasoner): T {
        return if (reasoner is DomainInterpreterReasoner) {
            reasoner.evalWithTrace(this) as T
        } else {
            use(reasoner) as T
        }
    }

    fun evalWithTrace(op: Operator): Any? {
        checkpoint(op)
        if (!expressionTraceState.enabled) {
            return op.use(this)
        }

        val traceNode = MutableExpressionTrace(op, iterationObject = iterationObject)
        expressionTraceState.add(traceNode)
        return try {
            val value = op.use(this)
            if (op !is Literal || op is VariableLiteral || op is DecisionTreeVarLiteral) {
                traceNode.value = value
                traceNode.isValueAnnotated = true
            }
            value
        } catch (e: ReasoningException) {
            if (e.expressionTrace == null) e.expressionTrace = expressionTraceState.trace
            throw e
        } catch (e: RuntimeException) {
            throw e.asReasoningException(expressionTrace = expressionTraceState.trace)
        } finally {
            expressionTraceState.removeLastActive(traceNode)
        }
    }

    private fun checkpoint(location: Any? = null) {
        control.checkpoint(location)
    }

    //---Присвоения---

    override fun process(op: AssignProperty) {
        val obj = op.objectExpr.evalAsRequiredObjDef("assign property '${op.propertyName}'")
        val value = op.valueExpr.evalAs<Any>()

        val propertyParams = obj.findPropertyDef(op.propertyName)!!.paramsDecl
        val paramsValues = NamedParamsValues(evalParamsToMap(op.paramsValues, propertyParams))

        obj.definedPropertyValues.addOrReplace(PropertyValueStatement(obj, op.propertyName, paramsValues, value))
    }

    override fun process(op: AssignDecisionTreeVar) {
        val value = op.valueExpr.evalAs<Obj>()

        if(!situation.decisionTreeVariables.containsKey(op.variableName))
            throw UnknownVariableException("Trying to assign variable '${op.variableName}' that has not been declared")

        situation.decisionTreeVariables[op.variableName] = value
    }

    override fun process(op: AddRelationshipLink) {
        val subj = op.subjectExpr.evalAsRequiredObjDef("add relationship '${op.relationshipName}'")
        val objectNames = op.objectExprs.map { it.evalAsRequiredObjDef("add relationship '${op.relationshipName}'").name }

        val relationshipParams = subj.findRelationshipDef(op.relationshipName)!!.effectiveParams
        val paramsValues = NamedParamsValues(evalParamsToMap(op.paramsValues, relationshipParams))

        subj.relationshipLinks.add(RelationshipLinkStatement(subj, op.relationshipName, objectNames, paramsValues))
    }

    override fun process(op: RemoveRelationshipLink) {
        val subj = op.subjectExpr.evalAsRequiredObjDef("remove relationship '${op.relationshipName}'")
        val relationship = subj.findRelationshipDef(op.relationshipName)!!
        val objectNames = op.objectExprs.map { it.evalAsRequiredObjDef("remove relationship '${op.relationshipName}'").name }

        val relationshipParams = relationship.effectiveParams
        val paramsValues = evalParamsToMap(op.paramsValues, relationshipParams)
        val unorderedObjectNames = if (relationship.isUnordered) objectNames.toHashSet() else emptySet()

        val matchingLinks = subj.relationshipLinks.filter { link ->
            link.relationshipName == op.relationshipName &&
                (if (relationship.isUnordered) link.objectNames.hasSameElementsAsSet(unorderedObjectNames)
                else link.objectNames == objectNames) &&
                link.paramsValues.matchesStrict(paramsValues, relationshipParams)
        }

        subj.relationshipLinks.removeAll(matchingLinks)
    }

    //---Управляющие конструкции

    override fun process(op: Block): Any? {
        var result: Any? = null
        var hasExpressions = false
        for (expr in op.nestedExprs) {
            checkpoint(op)
            result = evalWithTrace(expr)
            blockPrevious = result
            hasExpressions = true
        }
        if (!hasExpressions) {
            throw NoSuchElementException("List is empty.")
        }
        return result
    }

    override fun process(op: IfThen): Any? {
        val isConditionSatisfied = op.conditionExpr.evalAs<Boolean>()
        if (isConditionSatisfied) {
            val thenVal = evalWithTrace(op.thenExpr)
            if (op.elseExpr != null)
                return thenVal
            return null
        }
        return op.elseExpr?.let { evalWithTrace(it) }
    }

    //---Сравнения---

    override fun process(op: Compare): EnumValue {
        val valA = op.firstExpr.evalAs<Number>().toDouble()
        val valB = op.secondExpr.evalAs<Number>().toDouble()

        val res = compareValues(valA, valB)
        return when {
            res > 0 -> Comparison.Values.Greater
            res < 0 -> Comparison.Values.Less
            else -> Comparison.Values.Equal
        }
    }

    override fun process(op: CompareWithComparisonOperator): Boolean {
        val valA = op.firstExpr.evalAs<Any>()
        val valB = op.secondExpr.evalAs<Any>()

        return when(op.operator){
            CompareWithComparisonOperator.ComparisonOperator.Equal -> if (valA is Number && valB is Number) valA.toDouble() == valB.toDouble()
            else valA == valB

            CompareWithComparisonOperator.ComparisonOperator.NotEqual -> if (valA is Number && valB is Number) valA.toDouble() != valB.toDouble()
            else valA != valB
            CompareWithComparisonOperator.ComparisonOperator.Greater -> (valA as Number).toDouble() > (valB as Number).toDouble()
            CompareWithComparisonOperator.ComparisonOperator.GreaterEqual -> (valA as Number).toDouble() >= (valB as Number).toDouble()
            CompareWithComparisonOperator.ComparisonOperator.Less -> (valA as Number).toDouble() < (valB as Number).toDouble()
            CompareWithComparisonOperator.ComparisonOperator.LessEqual -> (valA as Number).toDouble() <= (valB as Number).toDouble()
        }
    }

    //---Поиск---

    override fun process(op: GetByCondition): Obj? {
        val f = getObjectsByCondition(op.conditionExpr, op.variable)

        //if (f.isEmpty())
        //    throw InterpretationException(NoSuchElementException("GetByCondition cannot find any objects that fit the condition"))
        if (f.size > 1)
            throw AmbiguousObjectException("GetByCondition found ${f.size} fitting objects: ${f.joinToString(limit = 5)}")

        return f.firstOrNull()
    }

    override fun process(op: GetExtreme): Obj? {
        val filtered = getObjectsByCondition(op.conditionExpr, TypedVariable(op.className, op.varName))

        if (filtered.isEmpty())
            return null
        //throw InterpretationException(NoSuchElementException("GetExtreme cannot find any objects that fit the condition"))

        val extreme = filtered.filter { obj ->
            //Проверяем, что текущий объект obj "экстремальней" всех остальных объектов other
            val isExtreme = filtered.filter { it != obj }.all { other ->
                val evalReasoner = if (expressionTraceState.enabled)
                    DomainInterpreterReasoner(situation, varContext.plus(op.varName to other).plus(op.extremeVarName to obj), blockPrevious, false, control)
                else
                    this.copy(varContext = varContext.plus(op.varName to other).plus(op.extremeVarName to obj))
                op.extremeConditionExpr.evalAs<Boolean>(evalReasoner)
            }
            if (expressionTraceState.enabled) expressionTraceState.addIteration(op.extremeConditionExpr, obj, isExtreme)
            isExtreme
        }

        //if (extreme.isEmpty())
        //throw throw InterpretationException(NoSuchElementException("GetExtreme cannot find any objects that fit the extreme condition"))
        if (extreme.size > 1)
            throw AmbiguousObjectException("GetExtreme found ${extreme.size} objects fitting the extreme condition: ${extreme.joinToString(limit = 5)}")

        return extreme.firstOrNull()
    }

    //---Вычисления---

    override fun process(op: GetClass): Clazz {
        val subj = op.objectExpr.evalAsRequiredObjDef("get object class")

        return subj.clazz.reference
    }

    override fun process(op: GetPropertyValue): Any {
        val obj = op.objectExpr.evalAsRequiredObjDef("read property '${op.propertyName}'")

        val propertyParams = obj.findPropertyDef(op.propertyName)!!.paramsDecl
        val paramsValuesMap = evalParamsToMap(op.paramsValues, propertyParams)

        return obj.getPropertyValue(op.propertyName, paramsValuesMap)
    }

    override fun process(op: GetByRelationship): Obj {
        val subj = op.subjectExpr.evalAsRequiredObjDef("get relationship '${op.relationshipName}'")
        val relationship = subj.findRelationshipDef(op.relationshipName)!!

        val relationshipParams = subj.findRelationshipDef(op.relationshipName)!!.effectiveParams
        val paramsValues = evalParamsToMap(op.paramsValues, relationshipParams)

        return RelationshipUtils.findSingleRelationshipLinkOrThrow(
            subj,
            relationship,
            objects = null,
            paramsValues = paramsValues
        ).objects[0].reference
    }

    override fun process(op: GetRelationshipParamValue): Any {
        val subj = op.subjectExpr.evalAsRequiredObjDef("read relationship '${op.relationshipName}' param '${op.paramName}'")
        val relationship = subj.findRelationshipDef(op.relationshipName)!!
        val objects = op.objectExprs.map {
            it.evalAsRequiredObjDef("read relationship '${op.relationshipName}' param '${op.paramName}'")
        }

        val relationshipParams = subj.findRelationshipDef(op.relationshipName)!!.effectiveParams
        val paramsValues = evalParamsToMap(op.paramsValues, relationshipParams)

        return RelationshipUtils.findSingleRelationshipLinkOrThrow(
            subj,
            relationship,
            objects,
            paramsValues
        ).paramsValues.asMap(relationshipParams)[op.paramName]!!
    }

    //---Типизация---

    override fun process(op: Cast): Obj {
        val subj = op.objectExpr.evalAsRequiredObjDef("cast object")
        val clazz = op.classExpr.evalAsRequiredClassDef("cast object")
        if (!subj.isInstanceOf(clazz))
            throw TypingException("Cannot cast $subj to type '${clazz.name}'")
        return subj.reference
    }

    override fun process(op: AddNewObject): Obj {
        val objName = DomainUtils.generateNewObjectName(situation.domainModel);
        val reasoner = this
        val contextProvider = object : BlueprintContextProvider {
            override fun provide(ctx: Blueprint<*>, name: String): Any {
                return when {
                    name == "objectName" -> objName
                    ctx is ObjectPropertyValueBlueprint && name == "value" -> {
                        ctx.value.evalAs<Any>(reasoner)
                    }
                    ctx is RelationshipLinkBlueprint && name == "names" -> {
                        ctx.value.map { it.evalAs<Obj>(reasoner).objectName }
                    }
                    ctx is RelationshipLinkBlueprint && name == "applyIf" -> {
                        ctx.applyIf.evalAsBoolean(reasoner)
                    }
                    else -> throw ReasoningMisuseException(
                        "Unknown blueprint context value '$name' for ${ctx::class.simpleName}"
                    )
                }
            }
        }

        val obj = op.objectDef.build(situation.domainModel, contextProvider)
        obj.validateAndThrow()
        situation.domainModel.objects.add(obj);
        return obj.reference
    }

    override fun process(op: CallProcedure): Any? {
        val scopeVars = if (blockPrevious == null) {
            varContext
        } else {
            varContext.plus("__blockPrevious" to blockPrevious!!)
        }
        val proc = ProcedureImpl.implFor(situation, op, scopeVars);
        val evaluatedArgs = op.arguments.mapIndexed { index, operator ->
            val arg = op.procedure.arguments[index]
            if (arg.type is ExpressionType) {
                operator
            } else operator.evalAs<Any>()
        }
        checkpoint(op)
        val result = proc.call(evaluatedArgs)
        checkpoint(op)
        if (result is Operator && op.procedure.returnType != ExpressionType) {
            return result.evalAs<Any>()
        }
        return result
    }

    //---Проверки---

    override fun process(op: CheckClass): Boolean {
        val subj = op.objectExpr.evalAsRequiredObjDef("check object class")
        val clazz = op.classExpr.evalAsRequiredClassDef("check object class")
        return subj.isInstanceOf(clazz)
    }

    override fun process(op: CheckRelationship): Boolean {
        val subj = op.subjectExpr.evalAsRequiredObjDef("check relationship '${op.relationshipName}'")
        val relationship = op.getRelationship(subj.clazz)
        val paramsValues = evalParamsToMap(op.paramsValues, relationship.effectiveParams)

        if (op.objectExprs.isEmpty()) {
            return subj.getProjection(relationship.subjectClass).all { projectedSubject ->
                projectedSubject.hasRelationshipWithAnyObjects(relationship, paramsValues)
            }
        }

        val objects = op.objectExprs.map { it.evalAsRequiredObjDef("check relationship '${op.relationshipName}'") }

        val classList = ArrayList<ClassDef>(relationship.objectClasses.size + 1)
        classList.add(relationship.subjectClass)
        classList.addAll(relationship.objectClasses)

        val projList = ArrayList<List<ObjectDef>>(objects.size + 1)
        projList.add(subj.getProjection(classList[0]))
        objects.forEachIndexed { index, obj ->
            projList.add(obj.getProjection(classList[index + 1]))
        }

        return allCombinationsMatch(projList) { objComb ->
            RelationshipUtils.hasRelationshipLink(
                objComb.first(),
                relationship,
                objComb.subList(1, objComb.size),
                paramsValues
            )
        }
    }

    //---Логические операции---

    override fun process(op: ExistenceQuantifier): Boolean? {
        val objects = getObjectsByCondition(op.selectorExpr, op.variable)
        for (obj in objects) {
            checkpoint(op)
            val value = this.copy(varContext = varContext.plus(op.variable.varName to obj)).evalWithTrace(op.conditionExpr)
            val booleanValue = value.asBooleanOrNull()
            //Продолжаем цикл только если встретили false - т.е. это булевский режим, и данный объект не подходит под условие
            if (booleanValue != false) {
                //Во всех остальных случаях возвращаемся
                return if (booleanValue == true)
                    true
                else
                    null
            }
        }
        //Если прошлись по всем объектам (или объектов и не
        return false
    }

    override fun process(op: ForAllQuantifier): Boolean? {
        val objects = getObjectsByCondition(op.selectorExpr, op.variable)
        val values = ArrayList<Any?>(objects.size)
        for (obj in objects) {
            checkpoint(op)
            val value = this.copy(varContext = varContext.plus(op.variable.varName to obj)).evalWithTrace(op.conditionExpr)
            val booleanValue = value.asBooleanOrNull()
            //Если в булевском режиме и встречаем false, то останавливаем сразу
            if (booleanValue == false) {
                return false
            }
            values.add(booleanValue)
        }
        //Возвращаем true, если все значения булевские true
        return if (values.all { it == true })
            true
        else
            null //в режиме цикла возвращаем null
    }

    override fun process(op: LogicalAnd): Boolean {
        return op.firstExpr.evalAs<Boolean>() && op.secondExpr.evalAs<Boolean>()
    }

    override fun process(op: LogicalNot): Boolean {
        return !op.operandExpr.evalAs<Boolean>()
    }

    override fun process(op: LogicalOr): Boolean {
        return op.firstExpr.evalAs<Boolean>() || op.secondExpr.evalAs<Boolean>()
    }

    //---Ссылки---

    override fun process(literal: VariableLiteral): Any {
        if (!varContext.containsKey(literal.name))
            throw UnknownVariableException("Context variable ${literal.name} not present during evaluation.")
        return varContext[literal.name]!!
    }

    override fun process(literal: DecisionTreeVarLiteral): Obj {
        if (!situation.decisionTreeVariables.containsKey(literal.name))
            throw UnknownVariableException("Decision tree variable ${literal.name} not present during evaluation.")
        return situation.decisionTreeVariables[literal.name]!!
    }

    override fun process(literal: ClassLiteral): Clazz {
        return Clazz(literal.name)
    }

    override fun process(literal: ObjectLiteral): Obj {
        return Obj(literal.name)
    }

    //---Значения---

    override fun process(literal: BooleanLiteral): Boolean {
        return literal.value
    }

    override fun process(literal: IntegerLiteral): Int {
        return literal.value
    }

    override fun process(literal: DoubleLiteral): Double {
        return literal.value
    }

    override fun process(literal: StringLiteral): String {
        return literal.value
    }

    override fun process(literal: EnumLiteral): EnumValue {
        return literal.value
    }

    //------------- Вспомогательные функции ------------

    private fun copy(
        situation: LearningSituation = this.situation,
        varContext: Map<String, Any> = this.varContext,
    ): DomainInterpreterReasoner {
        return DomainInterpreterReasoner(situation, varContext, blockPrevious, expressionTraceState, control)
    }

    private fun Operator.evalAsRequiredObjDef(action: String): ObjectDef {
        return evalAs<Obj?>()?.def ?: throw nullDomainReferenceException(action, this)
    }

    private fun Operator.evalAsRequiredClassDef(action: String): ClassDef {
        return evalAs<Clazz?>()?.def ?: throw nullDomainReferenceException(action, this)
    }

    private fun nullDomainReferenceException(action: String, expression: Operator): ReasoningException {
        val expressionText = runCatching {
            expression.description
        }.getOrElse {
            expression.toString()
        }
        val prefix = when (expression) {
            is GetByCondition, is GetExtreme -> "Find expression returned no object"
            else -> "Expression evaluated to null"
        }
        return ReasoningException(
            "$prefix while trying to $action: ${expression.javaClass.simpleName} $expressionText"
        )
    }

    private fun evalParamsToMap(paramsValuesExprList: ParamsValuesExprList, paramsDecl: ParamsDecl): Map<String, Any> {
        return paramsValuesExprList.asMap(paramsDecl).mapValues { it.value.evalAs<Any>() }
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

    private fun <T> allCombinationsMatch(lists: List<List<T>>, predicate: (combination: List<T>) -> Boolean): Boolean {
        return visitCombinations(lists) { predicate(it) }
    }

    private fun <T> anyCombinationMatches(lists: List<List<T>>, predicate: (combination: List<T>) -> Boolean): Boolean {
        var found = false
        visitCombinations(lists) {
            if (predicate(it)) {
                found = true
                false
            } else {
                true
            }
        }
        return found
    }

    private fun <T> visitCombinations(lists: List<List<T>>, visitor: (combination: List<T>) -> Boolean): Boolean {
        if (lists.any { it.isEmpty() }) {
            return true
        }

        val currentCombination = ArrayList<T>(lists.size)

        fun visit(depth: Int): Boolean {
            checkpoint()
            if (depth == lists.size) {
                return visitor(currentCombination)
            }

            for (element in lists[depth]) {
                checkpoint()
                currentCombination.add(element)
                val shouldContinue = visit(depth + 1)
                currentCombination.removeAt(currentCombination.lastIndex)
                if (!shouldContinue) {
                    return false
                }
            }
            return true
        }

        return visit(0)
    }

    private fun ObjectDef.getProjection(targetClass: ClassDef): List<ObjectDef> {
        if (this.isInstanceOf(targetClass)) return listOf(this)

        val projectionRelationship = this.clazz.getProjectionRelationship(targetClass)

        val projectedObjects = ArrayList<ObjectDef>(this.relationshipLinks.size)
        for (link in this.relationshipLinks) {
            if (link.relationshipName == projectionRelationship.name) {
                projectedObjects.add(Obj(link.objectNames.first()).def)
            }
        }
        return projectedObjects
    }

    private fun ObjectDef.hasRelationshipWithAnyObjects(
        relationship: RelationshipDef,
        paramsValues: Map<String, Any>,
    ): Boolean {
        if (RelationshipUtils.hasRelationshipLink(this, relationship, objects = null, paramsValues = paramsValues)) {
            return true
        }

        if (relationship.kind !is DependantRelationshipKind) {
            return false
        }

        val candidateObjectLists = relationship.objectClasses.map { it.instances }
        if (candidateObjectLists.any { it.isEmpty() }) {
            return false
        }

        return anyCombinationMatches(candidateObjectLists) { objectCombination ->
            RelationshipUtils.hasRelationshipLink(
                this,
                relationship,
                objectCombination,
                paramsValues
            )
        }
    }

    private fun ObjectDef.fitsCondition(condition: Operator, asVar: String): Boolean {
        return condition.evalAs<Boolean>(
            this@DomainInterpreterReasoner.copy(varContext = varContext.plus(asVar to this.reference))
        )
    }

    private fun ObjectDef.fitsConditionTraced(condition: Operator, asVar: String): Boolean {
        if (!expressionTraceState.enabled) return fitsCondition(condition, asVar)
        val iterationContext = varContext.plus(asVar to reference)
        val noTraceReasoner = DomainInterpreterReasoner(situation, iterationContext, blockPrevious, false, control)
        val result = try {
            condition.evalAsBoolean(noTraceReasoner)
        } catch (e: RuntimeException) {
            val tracedReasoner = this@DomainInterpreterReasoner.copy(varContext = iterationContext)
            tracedReasoner.evalWithTrace(condition, iterationObject = reference)
            throw e
        }
        if (result) {
            val tracedReasoner = this@DomainInterpreterReasoner.copy(varContext = iterationContext)
            tracedReasoner.evalWithTrace(condition, iterationObject = reference)
        } else {
            expressionTraceState.addIteration(condition, reference, result)
        }
        return result
    }

    override fun getObjectsByCondition(condition: Operator?, asVar: TypedVariable): List<Obj> { //обрабатываем случаи поиска типа $X == <выражение получения объекта>
        if (condition is CompareWithComparisonOperator && condition.operator == CompareWithComparisonOperator.ComparisonOperator.Equal) {
            if (condition.firstExpr == VariableLiteral(asVar.varName) && !condition.secondExpr.isDependantOnVariable(
                    asVar.varName
                )
            ) {
                return listOfNotNull(condition.secondExpr.evalAs<Obj?>())
            }

            if (condition.secondExpr == VariableLiteral(asVar.varName) && !condition.firstExpr.isDependantOnVariable(
                    asVar.varName
                )
            ) {
                return listOfNotNull(condition.firstExpr.evalAs<Obj?>())
            }
        }

        val objects = domain.objects.objectsAssignableTo(asVar.className)
        if (condition == null) return objects.map { it.reference }
        return objects.filter { it.fitsConditionTraced(condition, asVar.varName) }.map { it.reference }
    }

    private fun Operator.isDependantOnVariable(varName: String): Boolean {
        if (this is VariableLiteral && this.name == varName) {
            return true
        }
        return this.children.any { it.isDependantOnVariable(varName) }
    }

    private val <Def : DomainDef<Def>> DomainRef<Def>?.def: Def
        get() {
            if (this == null) {
                throw ReasoningException("Expected domain reference, but expression evaluated to null")
            }
            try {
                return this.findInOrUnkown(domain)
            } catch (e: UnknownDomainDefinitionException) {
                throw ReasoningException(e)
            }
        }
}
