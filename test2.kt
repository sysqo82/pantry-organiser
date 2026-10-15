import kotlinx.serialization.json.*

fun main() {
    val element: JsonElement = JsonArray(listOf(JsonPrimitive("i5w0ql3630r1hyz")))
    println(element is JsonArray)
    val first = element.jsonArray.firstOrNull()
    println(first is JsonPrimitive)
    if (first is JsonPrimitive) {
        println(first.isString)
    }
}
