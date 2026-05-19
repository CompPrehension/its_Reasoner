package its.reasoner.procedures

import its.model.definition.procedures.DebugPointDef
import its.reasoner.LearningSituation

class DebugPointImpl(procedure: DebugPointDef,
                     learningSituation: LearningSituation
) : ProcedureImpl<DebugPointDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>): Any {
        val message = evaluatedArguments[0]
        ReasonerOutput.println(message.toString())
        return scopeVar("__blockPrevious") ?: true
    }
}
