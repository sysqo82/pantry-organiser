import kotlinx.serialization.json.*

fun main() {
    val jsonStr = """
    {"collectionId":"pbc_3667139709","collectionName":"batch_payloads","id":"hbkhjzyav04zszc","items":["i5w0ql3630r1hyz"],"pantryId":"default-pantry","timestamp":1792057550714}
    """
    
    val json = Json { ignoreUnknownKeys = true }
    val obj = json.parseToJsonElement(jsonStr).jsonObject
    val itemsElement = obj["items"]
    
    println("itemsElement: ${itemsElement}")
    if (itemsElement is JsonArray) {
        val first = itemsElement.firstOrNull()
        println("first: $first")
        if (first is JsonPrimitive) {
            println("first.isString: ${first.isString}")
            println("first.content: ${first.content}")
            println("first.contentOrNull: ${first.contentOrNull}")
        }
    }
}
