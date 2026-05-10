package misc

import its.model.DomainSolvingModel
import its.model.definition.DomainModel
import its.model.definition.loqi.DomainLoqiBuilder
import its.model.definition.loqi.DomainLoqiWriter
import its.model.nodes.DecisionTree
import its.reasoner.LearningSituation
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.nodes.DecisionTreeTrace
import its.reasoner.procedures.ReasonerOutput
import its.reasoner.utils.formatDecisionTreeTrace
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
    subcommands = [ReasonCommand::class],
)
class CLI : Runnable {
    override fun run() {
        CommandLine(this).usage(System.out)
    }
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

        lateinit var model: DomainSolvingModel
        lateinit var baseDomain: DomainModel
        lateinit var decisionTree: DecisionTree
        lateinit var situation: LearningSituation
        val preparationTimeNanos = measureNanoTime {
            model = DomainSolvingModel(modelDir.toString(), DomainSolvingModel.BuildMethod.LOQI)
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
        val solveTimeNanos = measureNanoTime {
            if (isJsonl()) {
                ReasonerOutput.withSink(
                    outputSink = { message -> printJsonLine(reasonerOutputEvent(message)) },
                    action = { trace = decisionTree.solve(situation) },
                )
            } else {
                trace = decisionTree.solve(situation)
            }
        }

        if (isJsonl()) {
            printJsonLine(resultEvent(trace))
            printJsonLine(variablesEvent(trace))
            printJsonLine(traceEvent(trace, verbose))
            if (timeMeasure) {
                printJsonLine(metricEvent("preparationTime", preparationTimeNanos))
                printJsonLine(metricEvent("solveTime", solveTimeNanos))
            }
            exportDomainJsonl(situation.domainModel, baseDomain)
        } else {
            println(formatDecisionTreeTrace(trace, verbose))
            if (timeMeasure) {
                println("Preparation time: ${formatDuration(preparationTimeNanos)}")
                println("Solve time: ${formatDuration(solveTimeNanos)}")
            }

            exportDomainHuman(situation.domainModel, baseDomain)
        }

        return 0
    }

    private fun isJsonl(): Boolean = outputFormat.equals("jsonl", ignoreCase = true)

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

fun main(args: Array<String>) {
    val jsonlRequested = isJsonlRequested(args)
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
