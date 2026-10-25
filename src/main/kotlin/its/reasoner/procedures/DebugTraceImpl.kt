package its.reasoner.procedures

import its.model.definition.procedures.DebugTraceDef
import its.model.expressions.Operator
import its.reasoner.LearningSituation
import its.reasoner.ReasoningControl
import its.reasoner.operators.DomainInterpreterReasoner
import its.reasoner.utils.formatExpressionTraces

class DebugTraceImpl(
    procedure: DebugTraceDef,
    learningSituation: LearningSituation,
) : ProcedureImpl<DebugTraceDef>(procedure, learningSituation) {

    override fun process(evaluatedArguments: List<Any>): Any? {
        val expression = evaluatedArguments[0] as Operator
        val reasoner = DomainInterpreterReasoner(
            accessLearningSituation()!!,
            scopeVariables ?: mapOf(),
            collectExpressionTrace = true,
            control = ReasoningControl.NONE,
        )
        val value = reasoner.evalWithTrace(expression)
        ReasonerOutput.println("---- expression trace (value: $value) ----")
        ReasonerOutput.println(formatExpressionTraces(reasoner.expressionTrace))
        ReasonerOutput.println("---- end expression trace ----")
        return value
    }
}
