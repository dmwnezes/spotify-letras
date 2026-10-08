package com.dmwnezes.sintonia.history

import java.util.Locale
import kotlin.math.roundToInt

private val ptBR = Locale("pt", "BR")
private val WEEKDAYS = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")
private val MONTHS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

fun fmtHours(ms: Long): String {
    val h = ms / 3_600_000.0
    return when {
        h >= 100 -> "%,d h".format(ptBR, h.roundToInt())
        h >= 1 -> "%.1f h".format(ptBR, h)
        else -> "${(ms / 60_000).coerceAtLeast(0)} min"
    }
}

fun fmtInt(n: Int): String = "%,d".format(ptBR, n)

fun monthLabel(bucket: String): String {
    val m = bucket.substringAfter("-", "").toIntOrNull() ?: return bucket
    return MONTHS[m - 1]
}

/** Retrospectiva em texto, montada pelo próprio app a partir dos números. */
object Retrospective {

    fun forPeriod(stats: HistoryStats, key: String): String {
        val p = stats.periods[key] ?: return ""
        return if (key == "all") overall(stats, p) else year(stats, p)
    }

    private fun year(stats: HistoryStats, p: PeriodStats): String = buildString {
        val days = p.activeDays.coerceAtLeast(1)
        append("Em ${p.key} você ouviu ${fmtHours(p.ms)} de música, ")
        append("uma média de ${fmtHours(p.ms / days)} nos ${fmtInt(p.activeDays)} dias em que deu play. ")

        p.topArtists.firstOrNull()?.let { a ->
            append("${a.name} foi o artista do ano, com ${fmtHours(a.ms)}")
            val share = if (p.ms > 0) (100.0 * a.ms / p.ms).roundToInt() else 0
            if (share >= 5) append(" (${share}% de tudo o que você ouviu)")
            append(". ")
        }
        p.topTracks.firstOrNull()?.let { t ->
            append("A música que mais tocou foi \"${t.name}\", de ${t.sub}, ${fmtInt(t.count)} vezes. ")
        }
        if (p.newArtists > 0) {
            append("Você conheceu ${fmtInt(p.newArtists)} artistas novos")
            p.topNewArtists.firstOrNull()?.let { append("; o maior achado foi ${it.name}, que apareceu em ${it.sub}") }
            append(". ")
        }
        peakHour(p)?.let { append("Seu horário mais musical foi por volta das ${it}h") }
        peakWeekday(p)?.let { append(", e $it foi o dia em que você mais ouviu") }
        if (peakHour(p) != null) append(". ")

        p.monthMs.maxByOrNull { it.ms }?.let { append("O mês mais intenso foi ${monthLabel(it.name)}. ") }
        p.obsession?.takeIf { it.count >= 5 }?.let {
            append("Teve um dia em que \"${it.name}\" tocou ${it.count} vezes (${it.sub.substringAfter(" · ")}). ")
        }
        val prev = stats.periods[(p.key.toIntOrNull()?.minus(1)).toString()]
        if (prev != null && prev.ms > 0) {
            val diff = ((p.ms - prev.ms) * 100.0 / prev.ms).roundToInt()
            when {
                diff >= 5 -> append("Comparado com ${prev.key}, você ouviu $diff% a mais.")
                diff <= -5 -> append("Comparado com ${prev.key}, você ouviu ${-diff}% a menos.")
                else -> append("Você ouviu praticamente o mesmo que em ${prev.key}.")
            }
        }
    }.trim()

    private fun overall(stats: HistoryStats, p: PeriodStats): String = buildString {
        append("Desde ${p.firstDay ?: "o começo"}, você passou ${fmtHours(p.ms)} ouvindo música")
        val days = p.ms / 86_400_000.0
        if (days >= 1) append(" — o equivalente a ${"%.1f".format(ptBR, days)} dias inteiros sem parar")
        append(". Foram ${fmtInt(p.plays)} reproduções de ${fmtInt(p.distinctTracks)} músicas e ${fmtInt(p.distinctArtists)} artistas. ")
        p.topArtists.firstOrNull()?.let { append("${it.name} é o artista da sua vida até aqui, com ${fmtHours(it.ms)}. ") }
        p.topTracks.firstOrNull()?.let { append("E \"${it.name}\" é a sua música mais ouvida: ${fmtInt(it.count)} vezes. ") }

        val byYear = stats.years.mapNotNull { y -> stats.periods[y]?.topArtists?.firstOrNull()?.let { y to it.name } }.sortedBy { it.first }
        if (byYear.size >= 2) {
            append("Ano a ano, o seu artista nº 1 foi: ")
            append(byYear.joinToString(", ") { "${it.second} (${it.first})" })
            append(". ")
        }
        stats.years.mapNotNull { stats.periods[it] }.maxByOrNull { it.ms }?.let {
            if (stats.years.size >= 2) append("${it.key} foi o ano em que você mais ouviu. ")
        }
        if (p.longestStreakDays >= 7) {
            append("Sua maior sequência foi de ${p.longestStreakDays} dias seguidos com música (${p.streakFrom} a ${p.streakTo}). ")
        }
        p.biggestDay?.let { append("O dia recorde foi ${it.name}, com ${fmtHours(it.ms)}.") }
    }.trim()

    fun peakHour(p: PeriodStats): Int? = p.hourMs.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
    fun peakWeekday(p: PeriodStats): String? = p.weekdayMs.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { WEEKDAYS[it.index] }
}

/** Resumo em texto para mandar ao Claude (ou para qualquer app) pelo compartilhar do Android. */
object HistorySummary {

    fun build(stats: HistoryStats): String = buildString {
        val all = stats.all ?: return@buildString
        appendLine("Este é o resumo do meu histórico do Spotify, gerado pelo app Sintonia.")
        appendLine("Analise meus hábitos musicais e me conte coisas interessantes sobre como meu gosto mudou. Depois vou te fazer perguntas.")
        appendLine()
        appendLine("## Geral (${all.firstDay} a ${all.lastDay})")
        appendLine("- Tempo ouvindo música: ${fmtHours(all.ms)} em ${fmtInt(all.activeDays)} dias")
        appendLine("- Reproduções (30 s ou mais): ${fmtInt(all.plays)}; músicas diferentes: ${fmtInt(all.distinctTracks)}; artistas: ${fmtInt(all.distinctArtists)}")
        appendLine("- Taxa de músicas puladas: ${(all.skipRate * 100).roundToInt()}%; no aleatório: ${pct(all.shuffleStreams, all.streams)}; offline: ${pct(all.offlineStreams, all.streams)}")
        Retrospective.peakHour(all)?.let { appendLine("- Horário de pico: ${it}h; dia da semana preferido: ${Retrospective.peakWeekday(all)}") }
        if (all.podcastMs > 0) appendLine("- Podcasts: ${fmtHours(all.podcastMs)} (${all.topShows.take(3).joinToString { it.name }})")
        appendLine("- Aparelhos: " + all.platforms.take(4).joinToString { "${it.name} ${pctMs(it.ms, all.ms)}" })
        appendLine()
        appendLine("Top 15 artistas de todos os tempos: " + all.topArtists.take(15).joinToString { "${it.name} (${fmtHours(it.ms)})" })
        appendLine()
        appendLine("Top 15 músicas de todos os tempos: " + all.topTracks.take(15).joinToString { "${it.name} – ${it.sub} (${it.count}x)" })
        appendLine()
        appendLine("Top 10 álbuns: " + all.topAlbums.take(10).joinToString { "${it.name} – ${it.sub}" })
        if (all.mostSkipped.isNotEmpty()) {
            appendLine()
            appendLine("Mais puladas: " + all.mostSkipped.take(8).joinToString { "${it.name} (${it.sub})" })
        }
        if (stats.artistTimeline.isNotEmpty()) {
            appendLine()
            appendLine("Quando conheci meus principais artistas: " + stats.artistTimeline.take(25).joinToString { "${it.name} em ${it.sub}" })
        }
        for (y in stats.years.sorted()) {
            val p = stats.periods[y] ?: continue
            appendLine()
            appendLine("## $y — ${fmtHours(p.ms)}, ${fmtInt(p.plays)} reproduções, ${fmtInt(p.newArtists)} artistas novos")
            appendLine("Artistas: " + p.topArtists.take(8).joinToString { "${it.name} (${fmtHours(it.ms)})" })
            appendLine("Músicas: " + p.topTracks.take(8).joinToString { "${it.name} – ${it.sub} (${it.count}x)" })
            if (p.topNewArtists.isNotEmpty()) appendLine("Descobertas: " + p.topNewArtists.take(5).joinToString { it.name })
            val months = p.monthMs.joinToString { "${monthLabel(it.name).take(3)} ${fmtHours(it.ms)}" }
            appendLine("Por mês: $months")
        }
    }.trim()

    private fun pct(a: Int, b: Int) = if (b == 0) "0%" else "${(100.0 * a / b).roundToInt()}%"
    private fun pctMs(a: Long, b: Long) = if (b == 0L) "0%" else "${(100.0 * a / b).roundToInt()}%"
}
