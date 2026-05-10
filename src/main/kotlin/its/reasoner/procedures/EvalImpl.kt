package its.reasoner.procedures

import its.model.definition.procedures.EvalDef
import its.model.expressions.Operator
import its.reasoner.LearningSituation
import its.reasoner.operators.DomainInterpreterReasoner

class EvalImpl(procedure: EvalDef,
               learningSituation: LearningSituation
) : ProcedureImpl<EvalDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {
        val obj = evaluatedArguments[0] as Operator;
        val reasoner = DomainInterpreterReasoner(accessLearningSituation()!!)
        obj.use(reasoner);
    }
}