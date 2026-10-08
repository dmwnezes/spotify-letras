package com.dmwnezes.sintonia.wrapped

import com.dmwnezes.sintonia.history.HistoryStats
import com.dmwnezes.sintonia.history.PeriodStats
import com.dmwnezes.sintonia.history.Ranked
import com.dmwnezes.sintonia.history.Retrospective
import com.dmwnezes.sintonia.history.fmtHours
import com.dmwnezes.sintonia.history.fmtInt
import kotlin.math.roundToInt

/** Cada tela da retrospectiva animada. */
sealed interface Slide {
    data class Intro(val title: String, val subtitle: String) : Slide
    data class BigNumber(val kicker: String, val value: Long, val unit: String, val footnote: String) : Slide
    data class Spotlight(val kicker: String, val name: String, val detail: String, val extra: String?) : Slide
    data class TopList(val kicker: String, val items: List<Pair<String, String>>) : Slide
    data class Habits(val kicker: String, val persona: String, val detail: String, val hours: List<Long>) : Slide
    data class Summary(val period: String, val minutes: Long, val topArtists: List<String>, val topTracks: List<String>, val newArtists: Int) : Slide
}

object WrappedBuilder {

    fun build(stats: HistoryStats, key: String): List<Slide> {
        val p = stats.periods[key] ?: return emptyList()
        val isAll = key == "all"
        val period = if (isAll) "da sua vida musical" else "de $key"
        val slides = mutableListOf<Slide>()

        slides += Slide.Intro(
            title = if (isAll) "Sua história\nem música" else "Seu $key\nem música",
            subtitle = if (isAll) "${p.firstDay} a ${p.lastDay}" else "Toque para avançar",
        )

        val minutes = p.ms / 60_000
        val prev = stats.periods[(key.toIntOrNull()?.minus(1)).toString()]
        val compare = if (prev != null && prev.ms > 0) {
            val d = ((p.ms - prev.ms) * 100.0 / prev.ms).roundToInt()
            when {
                d >= 5 -> "$d% a mais que em ${prev.key}"
                d <= -5 -> "${-d}% a menos que em ${prev.key}"
                else -> "Quase igual a ${prev.key}"
            }
        } else "São ${"%.1f".format(p.ms / 86_400_000.0)} dias inteiros de música"
        slides += Slide.BigNumber("Você ouviu", minutes, "minutos", compare)

        p.topArtists.firstOrNull()?.let { a ->
            val share = if (p.ms > 0) (100.0 * a.ms / p.ms).roundToInt() else 0
            slides += Slide.Spotlight(
                "Seu artista nº 1 $period", a.name,
                "${fmtHours(a.ms)} · ${fmtInt(a.count)} reproduções",
                if (share >= 3) "$share% de tudo o que você ouviu" else null,
            )
        }
        if (p.topArtists.size >= 3) slides += Slide.TopList("Seus artistas mais ouvidos", p.topArtists.take(5).map { it.name to fmtHours(it.ms) })

        p.topTracks.firstOrNull()?.let { t ->
            slides += Slide.Spotlight("A música que não saiu da sua cabeça", t.name, t.sub, "${fmtInt(t.count)} vezes")
        }
        if (p.topTracks.size >= 3) slides += Slide.TopList("Suas músicas mais ouvidas", p.topTracks.take(5).map { it.name to "${it.sub} · ${it.count}x" })

        Retrospective.peakHour(p)?.let { h ->
            val persona = when (h) {
                in 0..4 -> "Coruja da madrugada"
                in 5..11 -> "Trilha sonora da manhã"
                in 12..17 -> "Embalo da tarde"
                else -> "Ouvinte da noite"
            }
            val day = Retrospective.peakWeekday(p)
            slides += Slide.Habits("Seu horário de pico: ${h}h", persona, day?.let { "E $it é o dia em que você mais dá play." } ?: "", p.hourMs)
        }

        if (!isAll && p.newArtists > 0) {
            val top = p.topNewArtists.take(3).joinToString(", ") { it.name }
            slides += Slide.Spotlight("Descobertas de $key", "${fmtInt(p.newArtists)} artistas novos", if (top.isNotBlank()) "Os maiores achados: $top" else "", null)
        }

        recordSlide(p)?.let { slides += it }

        slides += Slide.Summary(
            period = if (isAll) "Desde ${p.firstDay?.takeLast(4) ?: ""}" else key,
            minutes = minutes,
            topArtists = p.topArtists.take(5).map { it.name },
            topTracks = p.topTracks.take(5).map { it.name },
            newArtists = p.newArtists,
        )
        return slides
    }

    private fun recordSlide(p: PeriodStats): Slide? {
        val obs: Ranked? = p.obsession?.takeIf { it.count >= 5 }
        return when {
            obs != null -> Slide.Spotlight("Teve um dia de obsessão", "\"${obs.name}\"", "${obs.count} vezes num dia só", obs.sub.substringAfter(" · "))
            p.longestStreakDays >= 7 -> Slide.Spotlight("Sua maior sequência", "${p.longestStreakDays} dias seguidos", "${p.streakFrom} a ${p.streakTo}", null)
            p.biggestDay != null -> Slide.Spotlight("Seu dia recorde", p.biggestDay.name, fmtHours(p.biggestDay.ms), null)
            else -> null
        }
    }
}
