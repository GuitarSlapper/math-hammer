package ru.mathhammer

/** Parsing is independent of Android; incomplete edits remain explicit errors. */
fun parseInteger(text: String, range: IntRange): Int? {
    if (!text.matches(Regex("-?[0-9]+"))) return null
    return text.toIntOrNull()?.takeIf { it in range }
}
