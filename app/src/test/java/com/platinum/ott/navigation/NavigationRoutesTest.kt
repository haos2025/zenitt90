package com.platinum.ott.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Проверка маршрутов навигации по исходникам (без запуска приложения).
 *
 * Зачем: переход на несуществующий маршрут (`navigate("catalog_sources")` вместо `"sources"`)
 * компилируется, но роняет приложение в момент нажатия. Тест находит такие опечатки в CI.
 *
 * Как: собирает все `composable("...")` из исходников и сверяет с ними строковые литералы в
 * `navigate(...)` / `navigateToTab(...)`, а также маршруты в NavSidebar и PhoneBottomBar.
 * Переходы по маршруту из переменной (`navigate(route)`) не проверяются — их литералов нет.
 */
class NavigationRoutesTest {

    private val srcRoot: File = listOf("src/main/java", "app/src/main/java").map(::File).firstOrNull { it.isDirectory }
        ?: error("Не найдены исходники (src/main/java) — тест запущен не из каталога модуля app")

    private val ktFiles: List<File> by lazy { srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private val composableRegex = Regex("""composable\(\s*(?:route\s*=\s*)?"([^"]+)"""")
    private val literalRegex = Regex(""""((?:[^"\\]|\\.)*)"""")
    private val templateRegex = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")
    // Маршрут выглядит как "home", "detail/{}", "search?q=..." — отсеивает обычный текст и константы вроде "SERIES".
    private val looksLikeRoute = Regex("""^[a-z][a-z_]*(/.*)?(\?.*)?$""")
    // Элементы навигации: SEARCH("search", ...), MoreItem("series", ...).
    private val navItemRegex = Regex("""^\s*(?:[A-Z_]+|MoreItem)\(\s*"([a-z][a-z_]*)"""", RegexOption.MULTILINE)

    private val definedRoutes: List<String> by lazy {
        ktFiles.flatMap { f -> composableRegex.findAll(f.readText()).map { it.groupValues[1] }.toList() }
    }

    private fun segments(route: String): List<String> = route.substringBefore('?').trim('/').split('/')

    private fun matches(target: String, route: String): Boolean {
        val t = segments(target)
        val r = segments(route)
        if (t.size != r.size) return false
        return t.zip(r).all { (a, b) -> (b.startsWith("{") && b.endsWith("}")) || a == b || a == "{}" }
    }

    private data class Target(val file: String, val line: Int, val route: String)

    private fun navigateTargets(): List<Target> {
        val result = mutableListOf<Target>()
        for (file in ktFiles) {
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed
                val call = Regex("""\bnavigate(?:ToTab)?\(""").find(line) ?: return@forEachIndexed
                // Объявление функции (fun NavHostController.navigateToTab(route: String)) — не вызов.
                if (line.substring(0, call.range.first).contains("fun ")) return@forEachIndexed
                literalRegex.findAll(line.substring(call.range.last + 1)).forEach { m ->
                    val route = templateRegex.replace(m.groupValues[1], "{}")
                    if (looksLikeRoute.matches(route)) result += Target(file.name, index + 1, m.groupValues[1])
                }
            }
        }
        for (name in listOf("NavSidebar.kt", "PhoneBottomBar.kt")) {
            val file = ktFiles.firstOrNull { it.name == name } ?: continue
            navItemRegex.findAll(file.readText()).forEach { m ->
                result += Target(file.name, 0, m.groupValues[1])
            }
        }
        return result
    }

    @Test
    fun scannerFoundTheNavGraph() {
        // Защита от «тихого» прохождения: если путь к исходникам неверен, маршрутов будет 0.
        assertTrue("Найдено маршрутов: ${definedRoutes.size}", definedRoutes.size >= 20)
        assertTrue("Найдено переходов: ${navigateTargets().size}", navigateTargets().size >= 30)
    }

    @Test
    fun everyNavigateTargetHasARoute() {
        val missing = navigateTargets()
            .filter { target ->
                val normalized = templateRegex.replace(target.route, "{}")
                definedRoutes.none { matches(normalized, it) }
            }
            .map { "${it.file}:${it.line} → \"${it.route}\"" }
        assertEquals("Переходы на несуществующие маршруты:\n" + missing.joinToString("\n"), emptyList<String>(), missing)
    }

    @Test
    fun routesAreNotDefinedTwice() {
        val duplicates = definedRoutes.groupBy { it }.filter { it.value.size > 1 }.keys.toList()
        assertEquals("Маршруты объявлены дважды (второй молча перекроет первый): $duplicates", emptyList<String>(), duplicates)
    }
}
