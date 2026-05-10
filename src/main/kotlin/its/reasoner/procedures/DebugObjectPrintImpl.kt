package its.reasoner.procedures

import its.model.definition.ObjectRef
import its.model.definition.procedures.DebugObjectPrintDef
import its.reasoner.LearningSituation

class DebugObjectPrintImpl(procedure: DebugObjectPrintDef,
                         learningSituation: LearningSituation
) : ProcedureImpl<DebugObjectPrintDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>) {
        val obj = evaluatedArguments[0] as ObjectRef;
        ReasonerOutput.println(obj);
    }
}
