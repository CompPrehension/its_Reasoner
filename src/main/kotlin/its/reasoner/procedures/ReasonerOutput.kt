package its.reasoner.procedures

object ReasonerOutput {
    private val sink = ThreadLocal<(String) -> Unit>()

    fun println(message: Any?) {
        val output = message.toString()
        val currentSink = sink.get()
        if (currentSink != null) {
            currentSink(output)
        } else {
            kotlin.io.println(output)
        }
    }

    fun <T> withSink(outputSink: (String) -> Unit, action: () -> T): T {
        val previousSink = sink.get()
        sink.set(outputSink)
        try {
            return action()
        } finally {
            if (previousSink == null) {
                sink.remove()
            } else {
                sink.set(previousSink)
            }
        }
    }
}
