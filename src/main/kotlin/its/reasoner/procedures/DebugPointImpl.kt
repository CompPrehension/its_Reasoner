package its.reasoner.procedures

import its.model.definition.procedures.DebugPointDef
import its.reasoner.LearningSituation

class DebugPointImpl(procedure: DebugPointDef,
                     learningSituation: LearningSituation
) : ProcedureImpl<DebugPointDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {
        val message = evaluatedArguments[0] as String
        ReasonerOutput.println(message)
    }
}
