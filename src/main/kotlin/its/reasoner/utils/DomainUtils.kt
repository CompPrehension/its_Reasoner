package its.reasoner.utils

import its.model.definition.DomainModel

object DomainUtils {
    private const val AUTO_PREFIX = "auto_"
    private const val BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    fun generateNewObjectName(model: DomainModel, prefix: String = AUTO_PREFIX): String {
        var id = 0L
        while (true) {
            val candidate = prefix + id.toBase62()
            if (model.objects.get(candidate) == null) {
                return candidate
            }
            id++
        }
    }

    private fun Long.toBase62(): String {
        if (this == 0L) return BASE62_ALPHABET[0].toString()

        var value = this
        val result = StringBuilder()
        while (value > 0) {
            val index = (value % BASE62_ALPHABET.length).toInt()
            result.append(BASE62_ALPHABET[index])
            value /= BASE62_ALPHABET.length
        }
        return result.reverse().toString()
    }
}
