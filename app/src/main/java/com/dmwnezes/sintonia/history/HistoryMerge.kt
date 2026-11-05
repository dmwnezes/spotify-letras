package com.dmwnezes.sintonia.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Soma ao histórico importado (se houver) o que o app coletou depois do último dia dele. */
object HistoryMerge {

    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun combine(base: HistoryStats?, plays: List<RawStream>, zone: ZoneId = ZoneId.systemDefault()): HistoryStats? {
        val cutoff = base?.all?.lastDay?.let { LocalDate.parse(it, dateFmt) }
        val known = base?.let { b -> b.knownArtists + b.periods.values.flatMap { p -> p.topArtists.map { it.name } } }.orEmpty().toSet()
        val agg = HistoryAggregator(zone, known)
        plays.filter { p -> cutoff == null || Instant.ofEpochMilli(p.endEpochMs).atZone(zone).toLocalDate() > cutoff }
            .forEach(agg::add)
        val extra = agg.build(files = base?.files ?: 0, importedAtMs = base?.importedAtMs ?: 0)
        if (extra.all == null) return base
        if (base == null) return extra
        return HistoryStats(
            importedAtMs = base.importedAtMs,
            totalEntries = base.totalEntries + extra.totalEntries,
            files = base.files,
            periods = (base.periods.keys + extra.periods.keys).associateWith { k ->
                val a = base.periods[k]
                val b = extra.periods[k]
                if (a != null && b != null) merge(a, b) else (a ?: b)!!
            },
            // Tudo o que foi coletado é posterior ao importado, então a ordem por data se mantém.
            artistTimeline = base.artistTimeline + extra.artistTimeline.filter { it.name !in known },
            knownArtists = extra.knownArtists,
        )
    }

    private fun merge(a: PeriodStats, b: PeriodStats) = a.copy(
        ms = a.ms + b.ms,
        plays = a.plays + b.plays,
        streams = a.streams + b.streams,
        // shortcut: o importado só guarda os rankings, então música ou álbum fora do top conta como novo; só refazer com o histórico bruto salvo daria o número exato
        distinctTracks = a.distinctTracks + b.distinctTracks - overlap(a.topTracks, b.topTracks),
        distinctArtists = if (a.key == "all") a.distinctArtists + b.newArtists else a.distinctArtists + b.distinctArtists - overlap(a.topArtists, b.topArtists),
        distinctAlbums = a.distinctAlbums + b.distinctAlbums - overlap(a.topAlbums, b.topAlbums),
        activeDays = a.activeDays + b.activeDays, // os dias coletados vêm depois do corte, nunca repetem
        topArtists = sum(a.topArtists, b.topArtists, 50),
        topTracks = sum(a.topTracks, b.topTracks, 50),
        topAlbums = sum(a.topAlbums, b.topAlbums, 30),
        hourMs = a.hourMs.zip(b.hourMs, Long::plus),
        weekdayMs = a.weekdayMs.zip(b.weekdayMs, Long::plus),
        monthMs = sum(a.monthMs, b.monthMs, Int.MAX_VALUE).sortedBy { it.name },
        skipped = a.skipped + b.skipped,
        newArtists = a.newArtists + b.newArtists,
        topNewArtists = (a.topNewArtists + b.topNewArtists).sortedByDescending { it.ms }.take(10),
        biggestDay = listOfNotNull(a.biggestDay, b.biggestDay).maxByOrNull { it.ms },
        // shortcut: sequência que atravessa o corte não é emendada, conta o maior trecho; emendar exige comparar streakTo com o primeiro dia coletado
        longestStreakDays = maxOf(a.longestStreakDays, b.longestStreakDays),
        streakFrom = if (b.longestStreakDays > a.longestStreakDays) b.streakFrom else a.streakFrom,
        streakTo = if (b.longestStreakDays > a.longestStreakDays) b.streakTo else a.streakTo,
        obsession = listOfNotNull(a.obsession, b.obsession).maxByOrNull { it.count },
        firstDay = a.firstDay ?: b.firstDay,
        lastDay = b.lastDay ?: a.lastDay,
        // Plataforma, país, motivo de início, podcasts e pulos não vêm na API de recentes: seguem os do importado.
    )

    private fun id(r: Ranked) = "${r.name}|${r.sub}"

    private fun overlap(a: List<Ranked>, b: List<Ranked>): Int {
        val ids = a.mapTo(HashSet(), ::id)
        return b.count { id(it) in ids }
    }

    private fun sum(a: List<Ranked>, b: List<Ranked>, limit: Int): List<Ranked> =
        (a + b).groupBy(::id).values
            .map { same -> same.first().copy(ms = same.sumOf { it.ms }, count = same.sumOf { it.count }, uri = same.firstNotNullOfOrNull { it.uri }) }
            .sortedByDescending { it.ms }
            .take(limit)
}
