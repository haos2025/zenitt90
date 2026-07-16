package com.platinum.ott.core.plugin

/** Manifest, встраиваемый в начало каждого JS-плагина (Lampa-стиль) */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val type: PluginType = PluginType.SOURCE,
    val icon: String? = null,
    val component: String? = null,
    val css: String? = null,
    val requires: List<String> = emptyList(),
    val settings: List<PluginSetting> = emptyList()
)

enum class PluginType(val key: String) {
    SOURCE("source"),
    PARSER("parser"),
    INTERFACE("interface"),
    PLAYER("player"),
    TRACKER("tracker"),
    CATALOG("catalog"),
    FILTER("filter"),
    FULLSCREEN("fullscreen");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: SOURCE
    }
}

data class PluginSetting(
    val name: String,
    val title: String,
    val type: String = "input",
    val defaultValue: String = ""
)

/** Результат парсинга manifest-блока из JS-файла */
fun parseManifestFromScript(script: String): PluginManifest? {
    val regex = Regex("/\\*([\\s\\S]*?)\\*/", RegexOption.MULTILINE)
    val block = regex.find(script)?.groupValues?.getOrNull(1) ?: return null
    if (!block.contains("plugin")) return null

    fun extract(key: String): String? {
        val r = Regex("$key:\\s*['\"]?([^'\",\\n]+)", RegexOption.IGNORE_CASE)
        return r.find(block)?.groupValues?.getOrNull(1)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
    }
    fun extractList(key: String): List<String> {
        val r = Regex("$key:\\s*\\[([^\\]]*)\\]", RegexOption.IGNORE_CASE)
        return r.find(block)?.groupValues?.getOrNull(1)
            ?.split(",")?.map { it.trim().removeSurrounding("\"").removeSurrounding("'") }?.filter { it.isNotEmpty() } ?: emptyList()
    }

    val id = extract("id") ?: extract("plugin") ?: return null
    val name = extract("name") ?: id
    val version = extract("version") ?: "1.0"
    val typeKey = extract("type") ?: "source"
    return PluginManifest(
        id = id, name = name, version = version,
        description = extract("description") ?: "",
        author = extract("author") ?: "",
        type = PluginType.fromKey(typeKey),
        icon = extract("icon"),
        component = extract("component"),
        css = extract("css"),
        requires = extractList("requires")
    )
}
