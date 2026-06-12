package misc

import its.model.DomainSolvingModel
import its.model.definition.DomainModel
import its.model.definition.loqi.DomainLoqiBuilder
import its.model.definition.loqi.DomainLoqiWriter
import its.model.definition.loqi.OperatorLoqiBuilder
import its.model.nodes.DecisionTree
import its.reasoner.LearningSituation
import its.reasoner.ReasoningControl
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.nodes.DecisionTreeTrace
import its.reasoner.operators.ExpressionQueryManager
import its.reasoner.operators.ExpressionQueryResult
import its.reasoner.utils.branchResultExceptionsEvent
import its.reasoner.procedures.ReasonerOutput
import its.reasoner.utils.formatDecisionTreeTrace
import its.reasoner.utils.formatExpressionTraces
import its.reasoner.utils.metricEvent
import its.reasoner.utils.printJsonError
import its.reasoner.utils.printJsonLine
import its.reasoner.utils.reasonerOutputEvent
import its.reasoner.utils.resultEvent
import its.reasoner.utils.traceEvent
import its.reasoner.utils.variablesEvent
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.io.StringWriter
import java.nio.file.Path
import java.util.concurrent.Callable
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.system.measureNanoTime

@Command(
    name = "reasoner-cli",
    mixinStandardHelpOptions = true,
    version = ["its_Reasoner CLI"],
    description = ["CLI для запуска reasoning на LOQI DomainModel в контексте DomainSolvingModel"],
    subcommands = [ReasonCommand::class, ExpressionQueryCommand::class],
)
class CLI : Runnable {
    override fun run() {
        CommandLine(this).usage(System.out)
    }
}

@Command(
    name = "expression-query",
    aliases = ["expr-query"],
    mixinStandardHelpOptions = true,
    description = ["Runs a LOQI expression query on a domain and prints matching object names"],
)
class ExpressionQueryCommand : Callable<Int> {

    @Parameters(
        index = "0..*",
        arity = "2..3",
        paramLabel = "ARGS",
        description = ["DOMAIN_LOQI QUERY, or MODEL_DIR DOMAIN_LOQI QUERY"],
    )
    lateinit var args: List<String>

    @Option(
        names = ["--tag"],
        paramLabel = "TAG",
        description = ["Base model tag to merge with the specific domain"],
    )
    var tag: String? = null

    @Option(
        names = ["--debug"],
        description = ["Include debug metadata when building DomainSolvingModel"],
        defaultValue = "false",
    )
    var debug: Boolean = false

    @Option(
        names = ["--trace"],
        description = ["Print expression trace"],
        defaultValue = "false",
    )
    var trace: Boolean = false

    @Option(
        names = ["--verbose"],
        description = ["Print verbose expression trace"],
        defaultValue = "false",
    )
    var verbose: Boolean = false

    @Option(
        names = ["--limit"],
        paramLabel = "LIMIT",
        description = ["Maximum number of found objects to print"],
    )
    var limit: Int? = null

    @Option(
        names = ["--time-measure"],
        description = ["Measure query execution time, then print it in seconds and milliseconds"],
        defaultValue = "false",
    )
    var timeMeasure: Boolean = false

    @Option(
        names = ["--time-limit"],
        paramLabel = "SECONDS",
        description = ["Stop query execution after the given number of seconds"],
    )
    var timeLimitSeconds: Long? = null

    @Option(
        names = ["--format"],
        paramLabel = "FORMAT",
        description = ["Output format: human or jsonl"],
        defaultValue = "human",
    )
    lateinit var outputFormat: String

    override fun call(): Int {
        require(outputFormat.equals("human", ignoreCase = true) || outputFormat.equals("jsonl", ignoreCase = true)) {
            "Unsupported output format '$outputFormat'. Expected: human or jsonl"
        }
        limit?.let { require(it >= 0) { "Limit must be non-negative" } }
        timeLimitSeconds?.let { require(it > 0) { "Time limit must be positive" } }

        val (modelDir, domainLoqiFile, query) = parseArgs()
        val situation = buildExpressionQuerySituation(modelDir, domainLoqiFile, tag, debug)
        val expression = OperatorLoqiBuilder.buildExp(query)
        val control = timeLimitSeconds?.let(ReasoningControl::withTimeLimitSeconds) ?: ReasoningControl.NONE
        lateinit var result: ExpressionQueryResult
        val queryTimeNanos = measureNanoTime {
            result = ExpressionQueryManager(situation, control).query(
                expression = expression,
                collectTrace = trace,
                limit = limit,
            )
        }

        if (isJsonl()) {
            printJsonLine(
                mapOf(
                    "type" to "expression-query-result",
                    "objects" to result.objectRefs.map { it.objectName },
                )
            )
            if (trace) {
                printJsonLine(
                    mapOf(
                        "type" to "expression-trace",
                        "value" to formatExpressionTraces(result.trace, verbose),
                    )
                )
            }
            if (timeMeasure) {
                printJsonLine(metricEvent("queryTime", queryTimeNanos))
            }
        } else {
            println("Objects:")
            if (result.objectRefs.isEmpty()) {
                println("  <empty>")
            } else {
                result.objectRefs.forEach { println("  ${it.objectName}") }
            }
            if (trace) {
                println()
                println(formatExpressionTraces(result.trace, verbose))
            }
            if (timeMeasure) {
                println("Query time: ${formatDuration(queryTimeNanos)}")
            }
        }

        return 0
    }

    private fun parseArgs(): Triple<Path?, Path, String> =
        when (args.size) {
            2 -> Triple(null, Path.of(args[0]), args[1])
            3 -> Triple(Path.of(args[0]), Path.of(args[1]), args[2])
            else -> throw IllegalArgumentException("Expected DOMAIN_LOQI QUERY, or MODEL_DIR DOMAIN_LOQI QUERY")
        }

    private fun isJsonl(): Boolean = outputFormat.equals("jsonl", ignoreCase = true)
}

@Command(
    name = "reason",
    mixinStandardHelpOptions = true,
    description = ["Объединяет общую модель с LOQI domain-файлом, запускает reasoning и печатает результат"],
)
class ReasonCommand : Callable<Int> {

    @Parameters(index = "0", paramLabel = "MODEL_DIR", description = ["Директория DomainSolvingModel"])
    lateinit var modelDir: Path

    @Parameters(index = "1", paramLabel = "DOMAIN_LOQI", description = ["LOQI-файл со специфичной DomainModel"])
    lateinit var domainLoqiFile: Path

    @Option(
        names = ["--tag"],
        paramLabel = "TAG",
        description = ["Тег общей модели, который нужно объединить со специфичной моделью"],
    )
    var tag: String? = null

    @Option(
        names = ["--tree"],
        paramLabel = "TREE_NAME",
        description = ["Имя дерева решений. По умолчанию используется дерево без имени"],
        defaultValue = "",
    )
    lateinit var treeName: String

    @Option(
        names = ["--verbose"],
        description = ["Печатать дополнительные детали трассы, включая LOQI для одиночных Operator"],
        defaultValue = "false",
    )
    var verbose: Boolean = false

    @Option(
        names = ["--debug"],
        description = ["Include debug metadata when building DomainSolvingModel"],
        defaultValue = "false",
    )
    var debug: Boolean = false

    @Option(
        names = ["--no-trace"],
        description = ["Не печатать трассу в human-выводе"],
        defaultValue = "false",
    )
    var noTrace: Boolean = false

    @Option(
        names = ["-o", "--export-domain"],
        paramLabel = "OUTPUT_LOQI",
        description = ["Куда сохранить итоговую специфичную DomainModel после reasoning, за вычетом общей модели"],
    )
    var exportDomainFile: Path? = null

    @Option(
        names = ["--time-measure"],
        description = ["Measure preparation and solve execution time, then print them in seconds and milliseconds"],
        defaultValue = "false",
    )
    var timeMeasure: Boolean = false

    @Option(
        names = ["--time-limit"],
        paramLabel = "SECONDS",
        description = ["Stop reasoning after the given number of seconds"],
    )
    var timeLimitSeconds: Long? = null

    @Option(
        names = ["--format"],
        paramLabel = "FORMAT",
        description = ["Output format: human or jsonl"],
        defaultValue = "human",
    )
    lateinit var outputFormat: String

    @Option(
        names = ["--json-trace"],
        description = ["In jsonl output, print the trace value as structured JSON instead of a formatted string"],
        defaultValue = "false",
    )
    var jsonTrace: Boolean = false

    override fun call(): Int {
        require(outputFormat.equals("human", ignoreCase = true) || outputFormat.equals("jsonl", ignoreCase = true)) {
            "Unsupported output format '$outputFormat'. Expected: human or jsonl"
        }
        timeLimitSeconds?.let { require(it > 0) { "Time limit must be positive" } }

        lateinit var model: DomainSolvingModel
        lateinit var baseDomain: DomainModel
        lateinit var decisionTree: DecisionTree
        lateinit var situation: LearningSituation
        val preparationTimeNanos = measureNanoTime {
            model = DomainSolvingModel(
                modelDir.toString(),
                DomainSolvingModel.BuildMethod.LOQI,
                includeDebugMeta = debug,
            )
            baseDomain = resolveBaseDomain(model, tag)
            val specificDomain = domainLoqiFile.bufferedReader().use(DomainLoqiBuilder::buildDomain)
            val situationDomain = baseDomain.copy().apply {
                addMerge(specificDomain)
                validateAndThrow()
            }

            decisionTree = if (treeName.isEmpty()) model.decisionTree else model.decisionTree(treeName)
            situation = LearningSituation(situationDomain, solvingContext = model)
        }

        lateinit var trace: DecisionTreeTrace
        val control = timeLimitSeconds?.let(ReasoningControl::withTimeLimitSeconds) ?: ReasoningControl.NONE
        val solveTimeNanos = measureNanoTime {
            if (isJsonl()) {
                ReasonerOutput.withSink(
                    outputSink = { message -> printJsonLine(reasonerOutputEvent(message)) },
                    action = { trace = decisionTree.solve(situation, control) },
                )
            } else {
                trace = decisionTree.solve(situation, control)
            }
        }

        if (isJsonl()) {
            printJsonLine(resultEvent(trace))
            printJsonLine(variablesEvent(trace))
            printJsonLine(branchResultExceptionsEvent(trace))
            printJsonLine(jsonlTraceEvent(trace))
            if (timeMeasure) {
                printJsonLine(metricEvent("preparationTime", preparationTimeNanos))
                printJsonLine(metricEvent("solveTime", solveTimeNanos))
            }
            exportDomainJsonl(situation.domainModel, baseDomain)
        } else {
            println(if (noTrace) formatDecisionTreeSummary(trace) else formatDecisionTreeTrace(trace, verbose))
            val exceptionsSummary = formatBranchResultExceptionsSummary(trace)
            if (exceptionsSummary != null) {
                println()
                println(exceptionsSummary)
            }
            if (timeMeasure) {
                println("Preparation time: ${formatDuration(preparationTimeNanos)}")
                println("Solve time: ${formatDuration(solveTimeNanos)}")
            }

            exportDomainHuman(situation.domainModel, baseDomain)
        }

        return 0
    }

    private fun isJsonl(): Boolean = outputFormat.equals("jsonl", ignoreCase = true)

    private fun jsonlTraceEvent(trace: DecisionTreeTrace): Map<String, Any> =
        if (jsonTrace) {
            traceEvent(trace, verbose)
        } else {
            mapOf(
                "type" to "trace",
                "value" to formatDecisionTreeTrace(trace, verbose),
            )
        }

    private fun exportDomainHuman(domainModel: DomainModel, baseDomain: DomainModel) {
        exportDomainFile?.let { output ->
            saveSpecificDomain(domainModel, baseDomain, output)
            println()
            println("Specific domain saved to ${output.absolutePathString()}")
        }
    }

    private fun exportDomainJsonl(domainModel: DomainModel, baseDomain: DomainModel) {
        val output = exportDomainFile
        if (output == null || output.toString() == "-") {
            val loqi = writeSpecificDomainToString(domainModel, baseDomain)
            printJsonLine(
                mapOf(
                    "type" to "artifact",
                    "name" to "specificDomain",
                    "format" to "loqi",
                    "value" to loqi,
                )
            )
            return
        }

        saveSpecificDomain(domainModel, baseDomain, output)
        printJsonLine(
            mapOf(
                "type" to "service",
                "name" to "exportDomain",
                "status" to "success",
                "path" to output.absolutePathString(),
            )
        )
    }
}

private fun formatDecisionTreeSummary(trace: DecisionTreeTrace): String {
    val builder = StringBuilder()
    builder.appendLine("Result: ${trace.branchResult}")
    builder.appendLine("Variables:")
    if (trace.finalVariableSnapshot.isEmpty()) {
        builder.appendLine("  <empty>")
    } else {
        trace.finalVariableSnapshot.toSortedMap().forEach { (name, value) ->
            builder.appendLine("  $name = $value")
        }
    }
    return builder.toString().trimEnd()
}

private fun formatBranchResultExceptionsSummary(trace: DecisionTreeTrace): String? {
    val exceptions = trace.branchResultExceptions()
    if (exceptions.isEmpty()) {
        return null
    }

    val builder = StringBuilder()
    builder.appendLine("Exceptions:")
    exceptions.forEach { exception ->
        builder.appendLine(
            "  - id=${exception.nodeId}; result=${exception.result}; exceptionName=${exception.exceptionName}"
        )
    }
    return builder.toString().trimEnd()
}

private fun formatDuration(nanos: Long): String {
    val seconds = nanos / 1_000_000_000.0
    val millis = nanos / 1_000_000.0
    return "$seconds s; $millis ms"
}

private fun saveSpecificDomain(domainModel: DomainModel, baseDomain: DomainModel, output: Path) {
    val exportedSpecificDomain = domainModel.copy().apply {
        subtract(baseDomain)
    }
    output.bufferedWriter().use { writer ->
        DomainLoqiWriter.saveDomain(exportedSpecificDomain, writer)
    }
}

private fun writeSpecificDomainToString(domainModel: DomainModel, baseDomain: DomainModel): String {
    val exportedSpecificDomain = domainModel.copy().apply {
        subtract(baseDomain)
    }
    val writer = StringWriter()
    DomainLoqiWriter.saveDomain(exportedSpecificDomain, writer)
    return writer.toString()
}

private fun buildExpressionQuerySituation(
    modelDir: Path?,
    domainLoqiFile: Path,
    tag: String?,
    debug: Boolean,
): LearningSituation {
    val specificDomain = domainLoqiFile.bufferedReader().use(DomainLoqiBuilder::buildDomain)
    if (modelDir == null) {
        require(tag == null) { "--tag can be used only when MODEL_DIR is specified" }
        specificDomain.validateAndThrow()
        return LearningSituation(specificDomain)
    }

    val model = DomainSolvingModel(
        modelDir.toString(),
        DomainSolvingModel.BuildMethod.LOQI,
        includeDebugMeta = debug,
    )
    val baseDomain = resolveBaseDomain(model, tag)
    val situationDomain = baseDomain.copy().apply {
        addMerge(specificDomain)
        validateAndThrow()
    }
    return LearningSituation(situationDomain, solvingContext = model)
}

private fun resolveBaseDomain(model: DomainSolvingModel, tag: String?): DomainModel {
    if (tag == null) {
        return model.domainModel
    }

    require(model.tagsData.containsKey(tag)) {
        val knownTags = model.tagsData.keys.sorted().ifEmpty { listOf("<none>") }.joinToString(", ")
        "Tag '$tag' not found. Known tags: $knownTags"
    }
    return model.getMergedTagDomain(tag)
}

private fun isJsonlRequested(args: Array<String>): Boolean =
    args.withIndex().any { (index, arg) ->
        arg.equals("--format=jsonl", ignoreCase = true)
                || (arg == "--format" && args.getOrNull(index + 1).equals("jsonl", ignoreCase = true))
    }

private fun configureHumanConsoleEncoding() {
    val console = System.console() ?: return
    val charset = console.charset()
    System.setOut(PrintStream(FileOutputStream(FileDescriptor.out), true, charset))
    System.setErr(PrintStream(FileOutputStream(FileDescriptor.err), true, charset))
}

fun main(args: Array<String>) {
    val jsonlRequested = isJsonlRequested(args)
    if (!jsonlRequested) {
        configureHumanConsoleEncoding()
    }

    val commandLine = CommandLine(CLI())
    commandLine.executionExceptionHandler = CommandLine.IExecutionExceptionHandler { ex, _, parseResult ->
        if (jsonlRequested) {
            printJsonError(ex)
        } else {
            val commandName = parseResult.commandSpec().qualifiedName()
            System.err.println("$commandName failed:")
            ex.printStackTrace(System.err)
        }
        1
    }
    commandLine.parameterExceptionHandler = CommandLine.IParameterExceptionHandler { ex, _ ->
        if (jsonlRequested) {
            printJsonError(ex)
        } else {
            ex.printStackTrace(System.err)
            ex.commandLine.usage(System.err)
        }
        2
    }

    val exitCode = commandLine.execute(*args)
    if (exitCode != 0) {
        kotlin.system.exitProcess(exitCode)
    }
}
