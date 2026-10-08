package com.dmwnezes.sintonia.viz

enum class VizTheme(val label: String) {
    BRILHOS("Brilhos"),
    ONDAS("Ondas"),
    PARTICULAS("Partículas"),
    RETRO("Retrô");

    companion object {
        fun from(name: String): VizTheme = entries.firstOrNull { it.name == name } ?: BRILHOS
    }
}
