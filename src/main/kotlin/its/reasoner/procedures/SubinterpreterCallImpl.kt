package its.reasoner.procedures

import its.model.definition.DomainModel
import its.model.definition.procedures.CallableProcedureDef
import its.model.definition.procedures.MutableSubinterpreterCall
import its.model.definition.procedures.SubinterpreterProcedure
import its.model.definition.types.Obj
import its.model.nodes.ProcedureCallNode
import its.reasoner.LearningSituation
import its.reasoner.ReasoningMisuseException
import its.reasoner.SubinterpreterException
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.nodes.DecisionTreeTrace
import its.reasoner.utils.appendNodeMetadata
import its.reasoner.utils.formatDecisionTreeTrace

fun callSubinterpreter(treeName: String,
                       situation: LearningSituation,
                       args: List<Any?>,
                       sourceNode: ProcedureCallNode? = null
): DecisionTreeTrace {
    return executeSubinterpreter(treeName, situation, args, sourceNode).trace
}

private data class SubinterpreterExecutionResult(
    val trace: DecisionTreeTrace,
    val finalSituation: LearningSituation,
)

private fun DomainModel.replaceWith(other: DomainModel) {
    enums.clear()
    classes.clear()
    objects.clear()
    variables.clear()
    separateMetadata.keys.toList().forEach { separateMetadata.remove(it) }
    separateClassPropertyValues.keys.toList().forEach { separateClassPropertyValues.remove(it) }
    add(other)
}

private fun executeSubinterpreter(
    treeName: String,
    situation: LearningSituation,
    args: List<Any?>,
    sourceNode: ProcedureCallNode? = null
): SubinterpreterExecutionResult {
    val domain = situation.domainModel.copy()
    val solvingContext = requireNotNull(situation.solvingContext) {
        sourceNode?.appendNodeMetadata("Subinterpreters are disabled. Provide solvingContext to LearningSituation to enable this feature")
            ?: "Subinterpreters are disabled. Provide solvingContext to LearningSituation to enable this feature"
    }
    assert(solvingContext.decisionTrees.containsKey(treeName)) {
        sourceNode?.appendNodeMetadata("Subinterpreter cannot be created for unknown tree $treeName")
            ?: "Subinterpreter cannot be created for unknown tree $treeName"
    }
    val tree = solvingContext.decisionTrees[treeName]!!
    val variables = mutableMapOf<String, Obj>()
    for ((i, variable) in tree.variables.withIndex()) {
        if (i >= args.size) {
            throw ReasoningMisuseException(
                sourceNode?.appendNodeMetadata("Passed only $i variables to subinterpreter, but ${tree.variables.size} required")
                    ?: "Passed only $i variables to subinterpreter, but ${tree.variables.size} required"
            )
        }
        val arg = args[i]
        if (arg !is Obj) {
            throw ReasoningMisuseException(
                sourceNode?.appendNodeMetadata("Required only ObjectRef arguments, not ${arg?.javaClass?.simpleName}")
                    ?: "Required only ObjectRef arguments, not ${arg?.javaClass?.simpleName}"
            )
        }
        variables[variable.varName] = arg
    }
    val newSituation = LearningSituation(domain, variables, solvingContext)
    val result = tree.solve(newSituation)
    val branchException = result.resultingBranchResultException()
    if (branchException != null) {
        val baseMessage = "Subinterpreter '$treeName' ended with exception '${branchException.exceptionName}'"
        val messageWithContext = sourceNode?.appendNodeMetadata(baseMessage) ?: baseMessage
        val fullMessage = buildString {
            appendLine(messageWithContext)
            appendLine("Subinterpreter trace:")
            append(formatDecisionTreeTrace(result).prependIndent("  "))
        }
        throw SubinterpreterException(fullMessage, result, treeName)
    }
    return SubinterpreterExecutionResult(result, newSituation)
}

interface SubinterpreterImplFeatures {
    fun getResultingTrace(): DecisionTreeTrace?
}

class SubinterpreterCallImpl<T>(
    procedure: T,
    learningSituation: LearningSituation
) : ProcedureImpl<T>(procedure, learningSituation), SubinterpreterImplFeatures
        where T : CallableProcedureDef, T : SubinterpreterProcedure {

    private var trace: DecisionTreeTrace? = null

    override fun process(evaluatedArguments: List<Any>): Any {
        val situation = accessLearningSituation()
        assert(
            situation != null && situation.solvingContext != null
        ) { withNodeContext("Subinterpreters are disabled. Provide solvingContext to LearningSituation to enable this feature") }
        val treeName = evaluatedArguments[0] as String
        val result = callSubinterpreter(treeName, situation!!,
            evaluatedArguments.slice(1 until evaluatedArguments.size),
            sourceNode = nodeOrNull()
        )
        trace = result;
        return result.branchResult.toOptionalBool()
    }

    override fun getResultingTrace(): DecisionTreeTrace? {
        return trace
    }
}

class MutableSubinterpreterCallImpl(
    procedure: MutableSubinterpreterCall,
    learningSituation: LearningSituation
) : ProcedureImpl<MutableSubinterpreterCall>(procedure, learningSituation), SubinterpreterImplFeatures {

    private var trace: DecisionTreeTrace? = null

    override fun process(evaluatedArguments: List<Any>): Any {
        val situation = accessLearningSituation()
        assert(
            situation != null && situation.solvingContext != null
        ) { withNodeContext("Subinterpreters are disabled. Provide solvingContext to LearningSituation to enable this feature") }
        val treeName = evaluatedArguments[0] as String
        val result = executeSubinterpreter(treeName, situation!!,
            evaluatedArguments.slice(1 until evaluatedArguments.size),
            sourceNode = nodeOrNull()
        )
        trace = result.trace;
        situation.domainModel.replaceWith(result.finalSituation.domainModel)
        treeVariables.putAll(result.trace.finalVariableSnapshot)
        return result.trace.branchResult.toOptionalBool()
    }

    override fun getResultingTrace(): DecisionTreeTrace? {
        return trace
    }
}
