package misc

import its.model.DomainSolvingModel
import its.model.definition.DomainModel
import its.model.definition.loqi.DomainLoqiBuilder
import its.model.definition.loqi.DomainLoqiWriter
import its.reasoner.LearningSituation
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve
import its.reasoner.utils.formatDecisionTreeTrace
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.util.concurrent.Callable
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter

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

    override fun call(): Int {
        val model = DomainSolvingModel(modelDir.toString(), DomainSolvingModel.BuildMethod.LOQI)
        val baseDomain = resolveBaseDomain(model, tag)
        val specificDomain = domainLoqiFile.bufferedReader().use(DomainLoqiBuilder::buildDomain)
        val situationDomain = baseDomain.copy().apply {
            addMerge(specificDomain)
            validateAndThrow()
        }

        val decisionTree = if (treeName.isEmpty()) model.decisionTree else model.decisionTree(treeName)
        val situation = LearningSituation(situationDomain, solvingContext = model)
        val trace = decisionTree.solve(situation)

        println(formatDecisionTreeTrace(trace, verbose))

        exportDomainFile?.let { output ->
            val exportedSpecificDomain = situation.domainModel.copy().apply {
                subtract(baseDomain)
            }
            output.bufferedWriter().use { writer ->
                DomainLoqiWriter.saveDomain(exportedSpecificDomain, writer)
            }
            println()
            println("Specific domain saved to ${output.absolutePathString()}")
        }

        return 0
    }
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

fun main(args: Array<String>) {
    val commandLine = CommandLine(CLI())
    commandLine.executionExceptionHandler = CommandLine.IExecutionExceptionHandler { ex, _, parseResult ->
        val commandName = parseResult.commandSpec().qualifiedName()
        System.err.println("$commandName failed: ${ex.message ?: ex.javaClass.simpleName}")
        1
    }
    commandLine.parameterExceptionHandler = CommandLine.IParameterExceptionHandler { ex, _ ->
        System.err.println(ex.message)
        ex.commandLine.usage(System.err)
        2
    }

    val exitCode = commandLine.execute(*args)
    if (exitCode != 0) {
        kotlin.system.exitProcess(exitCode)
    }
}
