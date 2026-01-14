package its.reasoner.procedures

import its.model.definition.loqi.tree.DebugPointDef
import its.reasoner.LearningSituation

class DebugPointImpl(procedure: DebugPointDef,
                     learningSituation: LearningSituation
) : ProcedureImpl<DebugPointDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {
        val message = evaluatedArguments[0] as String
        println(message)
    }
}