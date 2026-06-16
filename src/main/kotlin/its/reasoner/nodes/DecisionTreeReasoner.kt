package its.reasoner.nodes

import its.model.ValueTuple
import its.model.definition.ThisShouldNotHappen
import its.model.definition.procedures.SubinterpreterProcedure
import its.model.definition.types.Obj
import its.model.expressions.Operator
import its.model.expressions.getUsedVariables
import its.model.nodes.*
import its.model.nodes.visitors.LinkNodeBehaviour
import its.reasoner.LearningSituation
import its.reasoner.ReasoningControl
import its.reasoner.ReasoningException
import its.reasoner.ReasoningOptions
import its.reasoner.asReasoningException
import its.reasoner.operators.OperatorReasoner
import its.reasoner.operators.OperatorReasoner.Companion.evalAs
import its.reasoner.procedures.ProcedureImpl
import its.reasoner.procedures.SubinterpreterImplFeatures
import its.reasoner.utils.appendNodeMetadata

/**
 * Ризонер дерева решений
 * Описывается как поведение узлов дерева решений, выдающее ответ на конкретный узел
 * @param situation текущая ситуация, описывающая задачу (изменяется ризонером)
 */
class DecisionTreeReasoner(
    val situation: LearningSituation,
    private val options: ReasoningOptions = ReasoningOptions.DEFAULT,
) : LinkNodeBehaviour<DecisionTreeTraceElement<*, *>> {

    private val exprReasoner = OperatorReasoner.defaultReasoner(situation, options)
    private fun <T> Operator.evalAs(): T = evalAs(exprReasoner)
    private fun checkpoint(location: Any? = null) = options.control.checkpoint(location)

    override fun process(node: CycleAggregationNode): AggregationDecisionTreeTraceElement<Obj> {
        checkpoint(node)
        val branchTracesMap = exprReasoner.getObjectsByCondition(node.selectorExpr, node.variable)
            .associateWith { obj ->
                checkpoint(node)
                situation.decisionTreeVariables[node.variable.varName] = obj
                val result = node.thoughtBranch.solve(situation, options)
                situation.decisionTreeVariables.remove(node.variable.varName)
                result
            }

        return aggregationTraceElement(node, branchTracesMap)
    }

    override fun process(node: WhileCycleNode): WhileCycleDecisionTreeTraceElement {
        val branchTraces = mutableListOf<DecisionTreeTrace>()
        while (node.conditionExpr.evalAs()) {
            checkpoint(node)
            val trace = node.thoughtBranch.solve(situation, options)
            branchTraces.add(trace)
            if (trace.branchResult != BranchResult.NULL) {
                break
            }
        }
        return WhileCycleDecisionTreeTraceElement(
            node,
            branchTraces.lastOrNull()?.branchResult ?: BranchResult.NULL,
            situation.decisionTreeVariables.toMap(),
            branchTraces
        )
    }

    private fun process(assignment: DecisionTreeVarAssignment): Boolean {
        val value = assignment.valueExpr.evalAs<Obj?>()
        if (value != null) {
            situation.decisionTreeVariables[assignment.variable.varName] = value
            return true
        }
        return false
    }

    override fun process(node: FindActionNode): LinkDecisionTreeTraceElement<Boolean> {
        val isFound = process(node.varAssignment)
        if (isFound) {
            node.secondaryAssignments.forEach {
                val secondaryFound = process(it)
                if (!secondaryFound)
                    throw ThisShouldNotHappen()
            }
        }
        return linkNodeTraceElement(node, isFound)
    }

    data class FindResult(
        val correct: List<Obj>,
        val errors: Map<FindErrorCategory, List<Obj>>,
    )

    fun searchWithErrors(node: CycleAggregationNode): FindResult {
        val iteratedValues = exprReasoner.getObjectsByCondition(node.selectorExpr, node.variable)

        val errors = mutableMapOf<FindErrorCategory, List<Obj>>()
        for (category in node.errorCategories.sortedBy { it.priority }) {
            val objects = exprReasoner.getObjectsByCondition(category.selectorExpr, category.checkedVariable)
            errors[category] = objects.filter { obj -> errors.values.none { it.contains(obj) } }
        }

        return FindResult(iteratedValues, errors)
    }

    fun processWithErrors(node: FindActionNode): FindResult {
        val isFound = process(node).nodeResult
        val allVariables = HashSet<String>(node.secondaryAssignments.size + 1)
        allVariables.add(node.varAssignment.variable.varName)
        node.secondaryAssignments.forEach { allVariables.add(it.variable.varName) }

        val errors = mutableMapOf<FindErrorCategory, List<Obj>>()
        for (category in node.errorCategories.sortedBy { it.priority }) {
            if (!isFound && category.selectorExpr.getUsedVariables().any { it in allVariables })
                continue

            val objects = exprReasoner.getObjectsByCondition(category.selectorExpr, category.checkedVariable)
            errors[category] = objects.filter { obj -> errors.values.none { it.contains(obj) } }
        }

        val correct = if (isFound) situation.decisionTreeVariables[node.varAssignment.variable.varName] else null
        return FindResult(listOf(correct).filterNotNull(), errors)
    }

    override fun process(node: BranchAggregationNode): AggregationDecisionTreeTraceElement<ThoughtBranch> {
        checkpoint(node)
        return aggregationTraceElement(
            node,
            node.thoughtBranches.associateWith {
                checkpoint(node)
                it.solve(situation, options)
            }
        )
    }

    private fun evaluateAggregation(
        aggregationMethod: AggregationMethod,
        nestedResults: Collection<BranchResult>
    ): BranchResult {
        return when (aggregationMethod) {
            AggregationMethod.AND ->
                if (nestedResults.all { it == BranchResult.NULL })
                    BranchResult.NULL
                else if (nestedResults.all { it == BranchResult.CORRECT || it == BranchResult.NULL })
                    BranchResult.CORRECT
                else
                    BranchResult.ERROR

            AggregationMethod.OR ->
                if (nestedResults.all { it == BranchResult.NULL })
                    BranchResult.NULL
                else if (nestedResults.any { it == BranchResult.CORRECT })
                    BranchResult.CORRECT
                else
                    BranchResult.ERROR

            AggregationMethod.HYP ->
                if (nestedResults.any { it == BranchResult.CORRECT })
                    BranchResult.CORRECT
                else if (nestedResults.any { it == BranchResult.ERROR })
                    BranchResult.ERROR
                else
                    BranchResult.NULL

            AggregationMethod.MUTEX -> nestedResults.singleOrNull { it != BranchResult.NULL } ?: BranchResult.NULL
        }
    }

    override fun process(node: QuestionNode): LinkDecisionTreeTraceElement<Any> {
        val value = node.expr.evalAs<Any>()
        return linkNodeTraceElement(node, value)
    }

    override fun processTupleQuestionNode(node: TupleQuestionNode): LinkDecisionTreeTraceElement<ValueTuple> {
        val exprTuple = ValueTuple(node.parts.map { it.expr.evalAs<Any>() })
        return linkNodeTraceElement(
            node,
            node.outcomes.keys.firstOrNull { it.matches(exprTuple) } ?: exprTuple
        )
    }

    override fun process(node: ProcedureCallNode): DecisionTreeTraceElement<*, *> {
        val evaluatedArgs = node.arguments.map { it.evalAs<Any>() }
        val procedure = ProcedureImpl.implFor(situation, node)
        procedure.call(evaluatedArgs)
        return linkNodeTraceElement(node, true)
    }

    private fun <AnswerType : Any> linkNodeTraceElement(
        node: LinkNode<AnswerType>,
        nodeResult: AnswerType,
    ) = LinkDecisionTreeTraceElement(node, nodeResult, situation.decisionTreeVariables.toMap())

    private fun <BranchInfo : Any> aggregationTraceElement(
        node: AggregationNode,
        branchTraceMap: Map<BranchInfo, DecisionTreeTrace>
    ) = AggregationDecisionTreeTraceElement(
        node,
        evaluateAggregation(node.aggregationMethod, branchTraceMap.values.map { it.branchResult }),
        situation.decisionTreeVariables.toMap(),
        branchTraceMap
    )

    companion object {

        /**
         * Вычислить текущий узел - получить для него ответ, либо готовый результат вычисления
         */
        @JvmStatic
        fun <T : Any> LinkNode<T>.execute(
            situation: LearningSituation,
            options: ReasoningOptions = ReasoningOptions.DEFAULT,
        ): DecisionTreeTraceElement<T, *> {
            options.control.checkpoint(this)
            return use(
                DecisionTreeReasoner(situation, options)
            ) as DecisionTreeTraceElement<T, *>
        }

        @JvmStatic
        fun <T : Any> LinkNode<T>.execute(
            situation: LearningSituation,
            control: ReasoningControl,
        ): DecisionTreeTraceElement<T, *> = execute(situation, ReasoningOptions(control = control))

        /**
         * Получить ответ на узел дерева решений
         */
        @JvmStatic
        fun <T : Any> LinkNode<T>.getAnswer(
            situation: LearningSituation,
            options: ReasoningOptions = ReasoningOptions.DEFAULT,
        ): T {
            return this.execute(situation, options).nodeResult
        }

        @JvmStatic
        fun <T : Any> LinkNode<T>.getAnswer(
            situation: LearningSituation,
            control: ReasoningControl,
        ): T = getAnswer(situation, ReasoningOptions(control = control))

        @JvmStatic
        private fun <T : Any> LinkNode<T>.getNextAny(answer: Any): DecisionTreeNode? {
            return this.getNextNode(answer as T)
        }

        /**
         * Получить корректный следующий узел
         */
        @JvmStatic
        fun <T : Any> LinkNode<T>.correctNext(
            situation: LearningSituation,
            options: ReasoningOptions = ReasoningOptions.DEFAULT,
        ): DecisionTreeNode? {
            return this.getNextNode(this.getAnswer(situation, options))
        }

        @JvmStatic
        fun <T : Any> LinkNode<T>.correctNext(
            situation: LearningSituation,
            control: ReasoningControl,
        ): DecisionTreeNode? = correctNext(situation, ReasoningOptions(control = control))

        /**
         * Прорешать ветвь мысли и получить трассу ее выполнение
         * @see DecisionTree.solve для прорешивания целого дерева
         */
        @JvmStatic
        fun ThoughtBranch.solve(
            situation: LearningSituation,
            options: ReasoningOptions = ReasoningOptions.DEFAULT,
        ): DecisionTreeTrace {
            var curr = this.start
            val traceElements = mutableListOf<DecisionTreeTraceElement<*, *>>()
            try {
                while (curr is LinkNode<*>) {
                    options.control.checkpoint(curr)
                    val traceElement = curr.execute(situation, options)
                    traceElements.add(traceElement)

                    val answer = traceElement.nodeResult
                    val next = curr.getNextAny(answer)
                    if (next == null) {
                        require(answer is BranchResult) {
                            if (curr is EndingNode)
                                curr.appendNodeMetadata("An evaluation result should have been formed at $curr (Reasoner error)")
                            else
                                curr.appendNodeMetadata("Node $curr has no outcome with value '$answer', but such an answer was returned")
                        }
                        return DecisionTreeTrace(traceElements)
                    }
                    curr = next
                }
                var redirectedTrace: DecisionTreeTrace? = null
                if (curr is BranchResultRedirectingNode) {
                    val impl = ProcedureImpl.implFor(situation, curr.call)
                    val nestedReasoner = OperatorReasoner.defaultReasoner(situation, options)
                    val evaluatedArgs = curr.call.arguments.map { it.evalAs<Any>(nestedReasoner) }
                    impl.call(evaluatedArgs)
                    redirectedTrace = (impl as SubinterpreterImplFeatures).getResultingTrace()!!;
                    curr.actionExpr?.use(nestedReasoner)
                    curr = redirectedTrace.resultingNode
                }
                require(curr is BranchResultNode) {
                    curr.appendNodeMetadata("The final node of the branch '$this' somehow wasn't a BranchResultNode (Reasoner error)")
                }
                curr.actionExpr?.use(OperatorReasoner.defaultReasoner(situation, options))
                if (redirectedTrace != null) {
                    traceElements.add(
                        RedirectedBranchResultDecisionTreeTraceElement(
                            curr, situation.decisionTreeVariables.toMap(),
                            redirectedTrace))
                } else {
                    traceElements.add(BranchResultDecisionTreeTraceElement(
                        curr, situation.decisionTreeVariables.toMap()))
                }
                return DecisionTreeTrace(traceElements)
            } catch (e: ReasoningException) {
                if (options.collectPartialTrace && e.partialDecisionTreeTrace == null)
                    e.partialDecisionTreeTrace = PartialDecisionTreeTrace(traceElements, curr, situation.decisionTreeVariables.toMap())
                throw e
            } catch (e: RuntimeException) {
                if (!options.collectPartialTrace) throw e
                throw e.asReasoningException(
                    partialDecisionTreeTrace = PartialDecisionTreeTrace(traceElements, curr, situation.decisionTreeVariables.toMap())
                )
            }
        }

        @JvmStatic
        fun ThoughtBranch.solve(
            situation: LearningSituation,
            control: ReasoningControl,
        ): DecisionTreeTrace = solve(situation, ReasoningOptions(control = control))

        /**
         * Прорешать дерево мысли для конкретной ситуации
         * При заходе в дерево проверяет наличие переменных [DecisionTree.variables],
         * и довычисляет [DecisionTree.implicitVariables], если это необходимо
         */
        @JvmStatic
        fun DecisionTree.solve(
            situation: LearningSituation,
            options: ReasoningOptions = ReasoningOptions.DEFAULT,
        ): DecisionTreeTrace {
            var failedNode: DecisionTreeNode? = null
            try {
                options.control.checkpoint(this)
                variables.forEach { variable ->
                    require(situation.decisionTreeVariables.containsKey(variable.varName)) {
                        "Decision tree requires variable '${variable.varName}' (${variable.className}), " +
                        "but it is not present in the learning situation"
                    }
                    val obj = situation.decisionTreeVariables[variable.varName]!!.findInOrUnkown(situation.domainModel)
                    require(obj.isInstanceOf(variable.className)) {
                        "Variable '${variable.varName}' was expected to be of class '${variable.className}', " +
                        "but object '$obj' is not an instance of it"
                    }
                }
                implicitVariables.forEach {
                    options.control.checkpoint(it)
                    if (!situation.decisionTreeVariables.containsKey(it.variable.varName)) {
                        DecisionTreeReasoner(
                            situation,
                            options,
                        ).process(it)
                    }
                }
                failedNode = null
                return mainBranch.solve(situation, options)
            } catch (e: ReasoningException) {
                if (options.collectPartialTrace && e.partialDecisionTreeTrace == null)
                    e.partialDecisionTreeTrace = PartialDecisionTreeTrace(emptyList(), failedNode, situation.decisionTreeVariables.toMap())
                throw e
            } catch (e: RuntimeException) {
                if (!options.collectPartialTrace) throw e
                throw e.asReasoningException(
                    partialDecisionTreeTrace = PartialDecisionTreeTrace(emptyList(), failedNode, situation.decisionTreeVariables.toMap())
                )
            }
        }

        @JvmStatic
        fun DecisionTree.solve(
            situation: LearningSituation,
            control: ReasoningControl,
        ): DecisionTreeTrace = solve(situation, ReasoningOptions(control = control))
    }
}
