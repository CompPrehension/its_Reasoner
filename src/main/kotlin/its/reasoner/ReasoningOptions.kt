package its.reasoner

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

data class ReasoningOptions(
    val control: ReasoningControl = ReasoningControl.NONE,
    val collectExpressionTrace: Boolean = false,
    val collectPartialTrace: Boolean = false,
) {
    companion object {
        @JvmField
        val DEFAULT = ReasoningOptions()
    }
}

class ReasoningControl private constructor(
    private val deadlineNanos: Long?,
    private val timeLimitSeconds: Long?,
    private val enabled: Boolean,
) {
    private var checkpointCounter: Int = 0

    fun checkpoint(location: Any? = null) {
        if (!enabled) {
            return
        }

        checkpointCounter++
        if ((checkpointCounter and CHECKPOINT_MASK) != 0) {
            return
        }

        if (Thread.currentThread().isInterrupted) {
            throw ReasoningInterruptedException()
        }

        val deadline = deadlineNanos
        if (deadline != null && System.nanoTime() >= deadline) {
            throw ReasoningTimeoutException(
                timeLimitSeconds = timeLimitSeconds ?: 0,
                locationDescription = location?.toString(),
            )
        }
    }

    companion object {
        private const val CHECKPOINT_MASK = 4095

        @JvmField
        val NONE = ReasoningControl(deadlineNanos = null, timeLimitSeconds = null, enabled = false)

        @JvmStatic
        fun withTimeLimitSeconds(timeLimitSeconds: Long): ReasoningControl {
            require(timeLimitSeconds > 0) { "Time limit must be positive" }
            val timeoutNanos = Math.multiplyExact(timeLimitSeconds, 1_000_000_000L)
            val deadlineNanos = Math.addExact(System.nanoTime(), timeoutNanos)
            return ReasoningControl(
                deadlineNanos = deadlineNanos,
                timeLimitSeconds = timeLimitSeconds,
                enabled = true,
            )
        }
    }
}
