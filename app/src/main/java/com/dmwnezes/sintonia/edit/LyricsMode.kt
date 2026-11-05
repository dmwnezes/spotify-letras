package com.dmwnezes.sintonia.edit

/** Os jeitos de mostrar a letra. */
enum class LyricsMode(val label: String, val hint: String) {
    EDIT("Edit", "Fundo preto, creme com brilho e VHS"),
    CLASSICO("Clássico", "Lista com o visualizer");

    companion object {
        // Quem tinha um estilo que foi removido volta para o Edit.
        fun from(s: String?) = entries.firstOrNull { it.name == s } ?: EDIT
    }
}
