package its.reasoner.procedures

import its.model.definition.DomainModel
import its.model.definition.ObjectRef
import its.model.definition.loqi.tree.AssertPointDef
import its.model.definition.loqi.tree.CallableProcedureDef
import its.model.definition.loqi.tree.DebugDumpPointDef
import its.model.definition.loqi.tree.DebugPointDef
import its.model.definition.types.Type
import its.model.nodes.ProcedureCallNode
import its.reasoner.LearningSituation
import its.reasoner.ReasoningMisuseException
import its.reasoner.TypingException

abstract sealed class ProcedureImpl<T : CallableProcedureDef>(val procedure: T, private val learningSituation: LearningSituation) {
    protected val variables: MutableMap<String, ObjectRef> = mutableMapOf()
    protected val model: DomainModel = learningSituation.domainModel

    init {
        loadVariables()
    }

    private fun loadVariables() {
        if (procedure.scopeCapture) {
            variables.putAll(learningSituation.decisionTreeVariables)
        }
    }

    private fun typeCheck(evaluatedArguments: List<Any>) {
        if (evaluatedArguments.size != procedure.arguments.size) {
            ReasoningMisuseException("Mismatch procedure arguments for ${procedure.name} (${evaluatedArguments.size}) != ${procedure.arguments})")
        }
        for (i in 0..evaluatedArguments.size) {
            if (!procedure.arguments[i].type.fits(evaluatedArguments[i], learningSituation.domainModel)) {
                throw TypingException("Mismatch type for argument `${i}` at ${procedure.name}, required ${procedure.arguments[i].type} " +
                        "(not ${Type.of(evaluatedArguments[i])})")
            }
        }
    }

    abstract fun process(evaluatedArguments: List<Any>)

    fun call(node: ProcedureCallNode, evaluatedArguments: List<Any>) {
        typeCheck(evaluatedArguments)
        process(evaluatedArguments)
    }

    companion object {
        fun implFor(situation: LearningSituation, call: ProcedureCallNode): ProcedureImpl<*> {
            return when (call.procedure) {
                is AssertPointDef -> AssertPointImpl(
                    call.procedure as AssertPointDef,
                    situation)
                is DebugDumpPointDef -> DebugDumpPointImpl(
                    call.procedure as DebugDumpPointDef,
                    situation
                )
                is DebugPointDef -> DebugPointImpl(
                    call.procedure as DebugPointDef,
                    situation
                )
                else -> { throw ReasoningMisuseException("Unknown procedure ${call.procedure.name} of ${call.procedure.javaClass.name}") }
            }
        }
    }
}