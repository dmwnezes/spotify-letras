package com.dmwnezes.sintonia.viz

import android.media.audiofx.Visualizer
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Lê o espectro do som que está saindo do celular (sessão de áudio 0 = saída geral),
 * incluindo o Spotify. Nada é gravado: só as intensidades de cada faixa de frequência.
 */
class AudioSpectrum(private val bands: Int = BANDS) {

    @Volatile var levels: FloatArray = FloatArray(bands); private set
    @Volatile var lastSoundAtMs: Long = 0L; private set
    @Volatile var startedAtMs: Long = 0L; private set

    private var visualizer: Visualizer? = null
    private val work = FloatArray(bands)

    /** Liga a captura. Devolve false se o aparelho não permitir. */
    fun start(): Boolean {
        stop()
        return try {
            val v = Visualizer(0)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            val rate = v.samplingRate / 1000f // mHz -> Hz
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: Visualizer?, wf: ByteArray?, sr: Int) = Unit
                override fun onFftDataCapture(vz: Visualizer?, fft: ByteArray?, sr: Int) {
                    if (fft != null) process(fft, rate)
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.enabled = true
            visualizer = v
            startedAtMs = System.currentTimeMillis()
            true
        } catch (t: Throwable) {
            stop()
            false
        }
    }

    fun stop() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
    }

    private fun process(fft: ByteArray, sampleRate: Float) {
        val n = fft.size / 2
        if (n < 4) return
        val binHz = (sampleRate / 2f) / n
        var energy = 0f
        for (b in 0 until bands) {
            // Faixas em escala logarítmica de 40 Hz a 16 kHz, como o ouvido percebe.
            val f0 = MIN_HZ * (MAX_HZ / MIN_HZ).pow(b / bands.toFloat())
            val f1 = MIN_HZ * (MAX_HZ / MIN_HZ).pow((b + 1) / bands.toFloat())
            val k0 = (f0 / binHz).toInt().coerceIn(1, n - 1)
            val k1 = (f1 / binHz).toInt().coerceIn(k0, n - 1)
            var sum = 0f
            for (k in k0..k1) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                sum += sqrt(re * re + im * im)
            }
            val mag = sum / (k1 - k0 + 1)
            // Agudos têm menos energia naturalmente: compensa um pouco.
            val tilt = 1f + 1.6f * (b / bands.toFloat())
            work[b] = (ln(1f + mag * tilt) / ln(1f + 48f)).coerceIn(0f, 1f)
            energy += mag
        }
        if (energy > bands * 0.6f) lastSoundAtMs = System.currentTimeMillis()
        levels = work.copyOf()
    }

    companion object {
        const val BANDS = 48
        private const val MIN_HZ = 40f
        private const val MAX_HZ = 16000f
    }
}
