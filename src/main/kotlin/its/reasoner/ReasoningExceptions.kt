package its.reasoner

import its.reasoner.nodes.DecisionTreeTrace
import its.reasoner.nodes.PartialDecisionTreeTrace
import its.reasoner.operators.ExpressionTrace


open class ReasoningException : IllegalArgumentException {
    var partialDecisionTreeTrace: PartialDecisionTreeTrace? = null
    var expressionTrace: List<ExpressionTrace>? = null

    constructor() : super()

    constructor(message: String) : super(message)

    constructor(cause: Throwable) : super(cause) {
        val reasonerCause = cause as? ReasoningException
        partialDecisionTreeTrace = reasonerCause?.partialDecisionTreeTrace
        expressionTrace = reasonerCause?.expressionTrace
    }

    constructor(
        message: String,
        cause: Throwable,
        partialDecisionTreeTrace: PartialDecisionTreeTrace? = (cause as? ReasoningException)?.partialDecisionTreeTrace,
        expressionTrace: List<ExpressionTrace>? = (cause as? ReasoningException)?.expressionTrace,
    ) : super(message, cause) {
        this.partialDecisionTreeTrace = partialDecisionTreeTrace
        this.expressionTrace = expressionTrace
    }
}

open class ReasoningMisuseException : ReasoningException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
}

open class AmbiguousObjectException : ReasoningException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
}

open class UnknownVariableException : ReasoningException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
}

open class TypingException : ReasoningException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
}

class SubinterpreterException(
    message: String,
    val subinterpreterTrace: DecisionTreeTrace,
    val subinterpreterTreeName: String,
) : ReasoningException(message)

/** Класс для поддержки точек останова отладчика при использовании процедуры `debug:breakpoint`*/
open class ReasonerBreakpointException : ReasoningException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
}

class ReasoningTimeoutException(
    val timeLimitSeconds: Long,
    val locationDescription: String? = null,
) : RuntimeException(
    if (locationDescription != null) {
        "Time limit (${timeLimitSeconds} seconds) exceeded at $locationDescription"
    } else {
        "Time limit (${timeLimitSeconds} seconds) exceeded"
    }
)

class ReasoningInterruptedException : RuntimeException("Reasoning was interrupted")

fun Throwable.asReasoningException(
    partialDecisionTreeTrace: PartialDecisionTreeTrace? = null,
    expressionTrace: List<ExpressionTrace>? = null,
): ReasoningException {
    val reasonerException = this as? ReasoningException
    return ReasoningException(
        message = message ?: javaClass.name,
        cause = this,
        partialDecisionTreeTrace = partialDecisionTreeTrace ?: reasonerException?.partialDecisionTreeTrace,
        expressionTrace = expressionTrace ?: reasonerException?.expressionTrace,
    )
}
