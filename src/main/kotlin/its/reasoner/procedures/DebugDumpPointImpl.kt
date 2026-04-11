package its.reasoner.procedures

import its.model.definition.procedures.DebugDumpPointDef
import its.reasoner.LearningSituation

class DebugDumpPointImpl(procedure: DebugDumpPointDef,
                         learningSituation: LearningSituation
) : ProcedureImpl<DebugDumpPointDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {
        val message = evaluatedArguments[0] as String
        println("<DEBUG>: $message")
        println("---- variable dump ----")
        for (variable in variables) {
            println("${variable.key}:\t\t ${variable.value.objectName}")
        }
        println("--- variable dump end ----")
    }
}