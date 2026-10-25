package its.reasoner.procedures

import its.model.definition.procedures.DebugDumpPointDef
import its.reasoner.LearningSituation

class DebugDumpPointImpl(procedure: DebugDumpPointDef,
                         learningSituation: LearningSituation
) : ProcedureImpl<DebugDumpPointDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>): Any {
        val message = evaluatedArguments[0] as String
        ReasonerOutput.println("<DEBUG>: $message")
        ReasonerOutput.println("---- tree variable dump ----")
        for (variable in treeVariables) {
            ReasonerOutput.println("${variable.key}:\t\t ${variable.value.objectName}")
        }
        if (scopeVariables != null && (scopeVariables as Map<out Any?, Any?>).isNotEmpty()) {
            ReasonerOutput.println("---- scope variable dump ----")
            for (variable in scopeVariables) {
                ReasonerOutput.println("${variable.key}:\t\t $variable")
            }
        }
        ReasonerOutput.println("--- variable dump end ----")
        return scopeVar("__blockPrevious") ?: true
    }
}
