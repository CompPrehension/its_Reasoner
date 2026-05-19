package its.reasoner.procedures

import its.model.definition.DomainModel
import its.model.definition.ObjectRef
import its.model.definition.procedures.AssertPointDef
import its.model.definition.procedures.CallableProcedureDef
import its.model.definition.procedures.DebugBreakpointDef
import its.model.definition.procedures.DebugDumpPointDef
import its.model.definition.procedures.DebugPointDef
import its.model.definition.procedures.DebugTraceDef
import its.model.definition.procedures.EvalDef
import its.model.definition.procedures.MutableSubinterpreterCall
import its.model.definition.procedures.SubinterpreterCall
import its.model.definition.types.ExpressionType
import its.model.definition.types.ObjectType
import its.model.definition.types.Type
import its.model.expressions.Operator
import its.model.expressions.operators.CallProcedure
import its.model.nodes.ProcedureCallNode
import its.reasoner.LearningSituation
import its.reasoner.ReasoningMisuseException
import its.reasoner.TypingException

sealed class ProcedureImpl<T : CallableProcedureDef>(val procedure: T, private val learningSituation: LearningSituation) {
    /** Буфер переменных дерева доступных процедуре, который используется в LearningSituation */
    protected val treeVariables: MutableMap<String, ObjectRef> = mutableMapOf()
    /** Буфер переменных текущей области видимости, т.е. область видимости текущего блока выражений, если процедура вызвана как выражение */
    protected var scopeVariables: Map<String, Any>? = null
        private set

    protected val model: DomainModel
        get() {
            return if (procedure.ensureMutable) {
                learningSituation.domainModel
            } else {
                learningSituation.domainModel.copy()
            }
        }

    init {
        loadVariables()
    }

    private fun loadVariables() {
        if (procedure.scopeCapture) {
            treeVariables.putAll(learningSituation.decisionTreeVariables)
        }
    }

    fun treeVar(name: String): ObjectRef? {
        return treeVariables[name]
    }

    fun scopeVar(name: String): Any? {
        return scopeVariables?.get(name) ?: treeVar(name)
    }

    private fun typeCheck(evaluatedArguments: List<Any>) {
        if (evaluatedArguments.size != procedure.arguments.size && !procedure.varArgs) {
            throw ReasoningMisuseException("Mismatch procedure arguments for ${procedure.name} (${evaluatedArguments.size}) != ${procedure.arguments})")
        }
        for ((i, element) in procedure.arguments.withIndex()) {
            val expectedType = element.type
            val actualValue = evaluatedArguments[i]
            val actualType = Type.of(actualValue)

            val typeFits = expectedType.fits(actualValue, learningSituation.domainModel)
            val untypedObjectAcceptsObject =
                expectedType is ObjectType && expectedType.isUntyped && actualType is ObjectType
            val rawExpressions = expectedType is ExpressionType && actualType == expectedType

            if (!typeFits && !untypedObjectAcceptsObject && !rawExpressions) {
                throw TypingException("Mismatch type for argument `${i}` at ${procedure.name}, required $expectedType " +
                        "(not $actualType)")
            }
        }
    }

    protected abstract fun process(evaluatedArguments: List<Any>): Any?

    fun call(evaluatedArguments: List<Any>): Any? {
        typeCheck(evaluatedArguments)
        val result = process(evaluatedArguments)
        val retType : Type<*>? = procedure.returnType
        if (retType != null && result == null) {
            throw TypingException("Result of procedure `${procedure.name}` wasn't returned, but required $retType")
        }
        if (retType != null && !retType.fits(result!!, learningSituation.domainModel)) {
            throw TypingException("Mismatch return type at ${procedure.name}, required $retType " +
                    "(not ${Type.of(result)})")
        }
        flush()
        return result;
    }

    protected fun accessLearningSituation(): LearningSituation? {
        return if (procedure.ensureMutable) learningSituation else null
    }

    private fun flush() {
        if (!procedure.ensureMutable) {
            return
        }
        learningSituation.domainModel.validateAndThrow()
        for ((name, value) in treeVariables) {
            learningSituation.decisionTreeVariables[name] = value
        }
    }

    companion object {
        fun implFor(situation: LearningSituation, call: ProcedureCallNode): ProcedureImpl<*> {
            return implFor(situation, call.asExpr())
        }

        fun implFor(situation: LearningSituation, call: CallProcedure,
                    scopeVars: Map<String, Any> = mapOf()
        ): ProcedureImpl<*> {
            return when (call.procedure) {
                is AssertPointDef -> AssertPointImpl(
                    call.procedure as AssertPointDef, situation)
                is DebugDumpPointDef -> DebugDumpPointImpl(
                    call.procedure as DebugDumpPointDef, situation
                )
                is DebugPointDef -> DebugPointImpl(
                    call.procedure as DebugPointDef, situation
                )
                is DebugTraceDef -> DebugTraceImpl(
                    call.procedure as DebugTraceDef, situation
                )
                is EvalDef -> EvalImpl(call.procedure as EvalDef, situation)
                is DebugBreakpointDef -> DebugBreakpointImpl(
                    call.procedure as DebugBreakpointDef, situation
                )
                is SubinterpreterCall -> SubinterpreterCallImpl(
                    call.procedure as SubinterpreterCall, situation)
                is MutableSubinterpreterCall -> MutableSubinterpreterCallImpl(
                    call.procedure as MutableSubinterpreterCall, situation)
                else -> { throw ReasoningMisuseException("Unknown procedure ${call.procedure.name} of ${call.procedure.javaClass.name}") }
            }.also {
                it.scopeVariables = scopeVars
            }
        }
    }
}
