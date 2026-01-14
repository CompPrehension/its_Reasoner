package its.reasoner.procedures

import its.model.definition.loqi.tree.AssertPointDef
import its.reasoner.LearningSituation
import its.reasoner.ReasoningException

class AssertPointImpl(procedure: AssertPointDef,
                      learningSituation: LearningSituation
) : ProcedureImpl<AssertPointDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {
        val result = evaluatedArguments[0] as Boolean
        val message = evaluatedArguments[1] as String
        if (!result) {
            throw ReasoningException("Assertion failed with message: $message")
        }
    }
}