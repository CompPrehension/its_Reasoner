package its.reasoner.utils

/**
 * Минимальный разбор JSON для проверки вывода: объекты - Map, массивы - List, числа - Double.
 */
object JsonParsing {

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipWhitespace()
        require(parser.position == text.length) { "Trailing characters at ${parser.position} in: $text" }
        return value
    }

    fun parseLines(text: String): List<Map<String, Any?>> {
        return text.lines().filter { it.isNotBlank() }.map {
            @Suppress("UNCHECKED_CAST")
            parse(it) as Map<String, Any?>
        }
    }

    private class Parser(private val text: String) {
        var position = 0

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun value(): Any? {
            skipWhitespace()
            return when (val char = text[position]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (char == '-' || char.isDigit()) number() else error("Unexpected '$char' at $position")
            }
        }

        private fun obj(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            position++
            skipWhitespace()
            if (text[position] == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = string()
                skipWhitespace()
                expect(':')
                result[key] = value()
                skipWhitespace()
                if (text[position] == ',') {
                    position++
                    continue
                }
                expect('}')
                return result
            }
        }

        private fun array(): List<Any?> {
            val result = ArrayList<Any?>()
            position++
            skipWhitespace()
            if (text[position] == ']') {
                position++
                return result
            }
            while (true) {
                result.add(value())
                skipWhitespace()
                if (text[position] == ',') {
                    position++
                    continue
                }
                expect(']')
                return result
            }
        }

        private fun string(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                when (val char = text[position++]) {
                    '"' -> return builder.toString()
                    '\\' -> when (val escaped = text[position++]) {
                        '"', '\\', '/' -> builder.append(escaped)
                        'b' -> builder.append('\b')
                        'f' -> builder.append('')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> {
                            builder.append(text.substring(position, position + 4).toInt(16).toChar())
                            position += 4
                        }
                        else -> error("Bad escape '\\$escaped' at $position")
                    }
                    else -> {
                        require(char.code >= 0x20) { "Unescaped control character at ${position - 1}" }
                        builder.append(char)
                    }
                }
            }
        }

        private fun number(): Double {
            val start = position
            while (position < text.length && (text[position].isDigit() || text[position] in "+-.eE")) position++
            return text.substring(start, position).toDouble()
        }

        private fun <T> literal(expected: String, value: T): T {
            require(text.startsWith(expected, position)) { "Expected $expected at $position" }
            position += expected.length
            return value
        }

        private fun expect(char: Char) {
            require(text[position] == char) { "Expected '$char' at $position, got '${text[position]}'" }
            position++
        }
    }
}
