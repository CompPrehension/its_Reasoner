package its.reasoner.procedures

import its.model.definition.procedures.DebugObjectPrintDef
import its.model.expressions.Operator
import its.reasoner.LearningSituation
import its.reasoner.operators.DomainInterpreterReasoner

class DebugObjectPrintImpl(procedure: DebugObjectPrintDef,
                         learningSituation: LearningSituation
) : ProcedureImpl<DebugObjectPrintDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {
        val obj = evaluatedArguments[0] as Operator;
        val reasoner = DomainInterpreterReasoner(accessLearningSituation()!!)
        val result = obj.use(reasoner);
        println(result);
    }
}