package its.reasoner.procedures

import its.model.definition.procedures.EvalDef
import its.reasoner.LearningSituation

class EvalImpl(procedure: EvalDef,
               learningSituation: LearningSituation
) : ProcedureImpl<EvalDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {

    }
}