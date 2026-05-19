package its.reasoner.procedures

import its.model.definition.procedures.CallableProcedureDef
import its.model.definition.procedures.MutableSubinterpreterCall
import its.model.definition.procedures.SubinterpreterProcedure
import its.model.definition.types.Obj
import its.reasoner.LearningSituation
import its.reasoner.ReasoningMisuseException
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.nodes.DecisionTreeTrace

fun callSubinterpreter(treeName: String,
                       situation: LearningSituation,
                       args: List<Any?>
): DecisionTreeTrace {
    val domain = situation!!.domainModel.copy()
    assert(situation.solvingContext!!.decisionTrees.containsKey(treeName)) {
        "Subinterpreter cannot be created for unknown tree $treeName"
    }
    val tree = situation.solvingContext!!.decisionTrees[treeName]!!
    val variables = mutableMapOf<String, Obj>()
    for ((i, variable) in tree.variables.withIndex()) {
        if (i >= args.size) {
            throw ReasoningMisuseException("Passed only $i variables to subinterpreter, but ${tree.variables.size} required");
        }
        val arg = args[i]
        if (arg !is Obj) {
            throw ReasoningMisuseException("Required only ObjectRef arguments, not ${arg?.javaClass?.simpleName}");
        }
        variables[variable.varName] = arg
    }
    val newSituation = LearningSituation(domain, variables, situation.solvingContext)
    return tree.solve(newSituation);
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
        ) { "Subinterpreters are disabled. Provide solvingContext to LearningSituation to enable this feature" }
        val treeName = evaluatedArguments[0] as String
        val result = callSubinterpreter(treeName, situation!!,
            evaluatedArguments.slice(1 until evaluatedArguments.size))
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
        ) { "Subinterpreters are disabled. Provide solvingContext to LearningSituation to enable this feature" }
        val treeName = evaluatedArguments[0] as String
        val result = callSubinterpreter(treeName, situation!!,
            evaluatedArguments.slice(1 until evaluatedArguments.size))
        trace = result;
        treeVariables.putAll(result.finalVariableSnapshot)
        return result.branchResult.toOptionalBool()
    }

    override fun getResultingTrace(): DecisionTreeTrace? {
        return trace
    }
}
