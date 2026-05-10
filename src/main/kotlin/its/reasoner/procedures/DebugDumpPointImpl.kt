package its.reasoner.procedures

import its.model.definition.procedures.DebugDumpPointDef
import its.reasoner.LearningSituation

class DebugDumpPointImpl(procedure: DebugDumpPointDef,
                         learningSituation: LearningSituation
) : ProcedureImpl<DebugDumpPointDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {
        val message = evaluatedArguments[0] as String
        ReasonerOutput.println("<DEBUG>: $message")
        ReasonerOutput.println("---- variable dump ----")
        for (variable in variables) {
            ReasonerOutput.println("${variable.key}:\t\t ${variable.value.objectName}")
        }
        ReasonerOutput.println("--- variable dump end ----")
    }
}
