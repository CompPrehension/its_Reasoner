package its.reasoner.utils

import its.model.definition.DomainModel
import its.model.definition.types.Obj

/**
 * Represents an object reference as a JSON-compatible structure with its resolved
 * type and metadata (when the object is known in [domainModel]).
 */
fun Obj.toJsonValue(domainModel: DomainModel): Map<String, Any?> {
    val objectDef = findIn(domainModel)
    return mapOf(
        "repr_name" to toString(),
        "object_name" to objectName,
        "type" to objectDef?.className,
        "metadata" to objectDef?.metadata?.entries.orEmpty().map { entry ->
            mapOf("name" to entry.propertyName, "locCode" to entry.locCode, "value" to entry.value)
        },
    )
}
