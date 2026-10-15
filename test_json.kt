import kotlinx.serialization.json.*

fun main() {
    val jsonStr = """
    {
        "id": "e0ukdl6v2u1bz6j",
        "pantryId": "default-pantry",
        "items": [
            "i5w0ql3630r1hyz"
        ]
    }
    """
    
    val json = Json { ignoreUnknownKeys = true }
    val obj = json.parseToJsonElement(jsonStr).jsonObject
    val itemsElement = obj["items"]
    
    if (itemsElement is JsonArray) {
        val first = itemsElement.firstOrNull()
        println("first: $first")
        println("first is JsonPrimitive: ${first is JsonPrimitive}")
        if (first is JsonPrimitive) {
            println("first.isString: ${first.isString}")
        }
    }
}
