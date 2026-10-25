package misc

import its.reasoner.ReasonerFixtures
import its.reasoner.utils.JsonParsing
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Запуск CLI в процессе с перехватом потоков вывода и модель решения на диске.
 */
object CliFixture {

    const val MAIN_TREE = """
        tpg Main(X: item) {
            var Y: item = X->next;
            ask (Y.sold) out true else { conclude: error [ id = "notSold" ; exception = "true" ; exceptionName = "NotSold" ] };
            conclude: correct [ id = "ok" ]
        }
    """

    const val ALT_TREE = """
        tpg Alt(X: item) {
            eval(X.sold = true);
            conclude: correct
        }
    """

    const val DEBUG_TREE = """
        tpg Dbg(X: item) {
            debug:print("debug tree");
            conclude: correct
        }
    """

    const val TAG_EXTRA = """
        obj z : item { weight = 9 ; price = 9.0 ; color = Color:red ; sold = true ; }
    """

    class CliRun(val exitCode: Int, val stdout: String, val stderr: String) {
        val stdoutEvents get() = JsonParsing.parseLines(stdout)
        val stderrEvents get() = JsonParsing.parseLines(stderr)
    }

    fun run(vararg args: String): CliRun {
        val jsonl = args.any { it.equals("jsonl", ignoreCase = true) || it.equals("--format=jsonl", ignoreCase = true) }
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        System.setOut(PrintStream(out, true, Charsets.UTF_8))
        System.setErr(PrintStream(err, true, Charsets.UTF_8))
        val exitCode = try {
            System.setProperty("picocli.ansi", "false")
            createCommandLine(jsonl).execute(*args)
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
        return CliRun(exitCode, out.toString(Charsets.UTF_8), err.toString(Charsets.UTF_8))
    }

    fun modelDir(root: Path, withDebugTree: Boolean = false): Path {
        val dir = Files.createDirectories(root.resolve("model"))
        dir.resolve("domain.loqi").writeText(ReasonerFixtures.ITEMS_CLASSES)
        dir.resolve("tag_extra.loqi").writeText(TAG_EXTRA)
        dir.resolve("tree.loqi").writeText(MAIN_TREE)
        dir.resolve("tree_alt.loqi").writeText(ALT_TREE)
        if (withDebugTree) dir.resolve("tree_dbg.loqi").writeText(DEBUG_TREE)
        return dir
    }

    fun situationFile(root: Path, variable: String = "a"): Path {
        val file = root.resolve("situation.loqi")
        file.writeText(ReasonerFixtures.ITEMS_OBJECTS + "\nvar X = $variable\n")
        return file
    }

    fun standaloneDomainFile(root: Path): Path {
        val file = root.resolve("domain.loqi")
        file.writeText(ReasonerFixtures.ITEMS_DOMAIN + "\nvar X = a\n")
        return file
    }
}
