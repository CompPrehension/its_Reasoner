package its.reasoner


open class ReasoningMisuseException : IllegalArgumentException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
}

open class ReasoningException : IllegalArgumentException {
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

/** Класс для поддержки точек останова отладчика при использовании процедуры `debug:breakpoint`*/
open class ReasonerBreakpointException : ReasoningException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(cause: Throwable) : super(cause)
}