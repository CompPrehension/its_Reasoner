package its.reasoner.procedures

import its.model.definition.procedures.DebugBreakpointDef
import its.reasoner.LearningSituation
import its.reasoner.ReasonerBreakpointException

class DebugBreakpointImpl(procedure: DebugBreakpointDef,
                     learningSituation: LearningSituation
) : ProcedureImpl<DebugBreakpointDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {
        val message = evaluatedArguments[0] as String
        throw ReasonerBreakpointException(withNodeContext("Debug breakpoint with message: $message"))
    }
}
