// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player.eq

/** The 10 reference bands every preset is defined on (Hz). */
val EQ_BANDS_HZ = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

/** Presets in dB per reference band (±12). [CUSTOM] starts flat and holds the user's own bands. */
enum class EqPreset(val gains: FloatArray) {
    FLAT(FloatArray(10)),
    BASS_BOOST(floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    TREBLE_BOOST(floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)),
    VOCAL(floatArrayOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)),
    ROCK(floatArrayOf(5f, 4f, 2f, -1f, -2f, -1f, 1f, 3f, 4f, 5f)),
    POP(floatArrayOf(-1f, 1f, 3f, 4f, 3f, 1f, -1f, -1f, 1f, 2f)),
    ELECTRONIC(floatArrayOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f)),
    HIP_HOP(floatArrayOf(5f, 4f, 2f, 3f, -1f, -1f, 1f, -1f, 2f, 3f)),
    ACOUSTIC(floatArrayOf(4f, 3f, 2f, 1f, 1f, 1f, 2f, 3f, 3f, 2f)),
    CUSTOM(FloatArray(10)),
}

/** The equalizer as stored (a device preference): off by default. [gains] are the 10 reference bands. */
data class EqSettings(
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    val gains: List<Float> = List(10) { 0f },
    /** dB added before the bands, −12..+12. */
    val preampDb: Float = 0f,
) {
    fun choose(p: EqPreset) = copy(preset = p, gains = if (p == EqPreset.CUSTOM) gains else p.gains.toList())
    fun setBand(i: Int, db: Float) = copy(preset = EqPreset.CUSTOM, gains = gains.toMutableList().also { it[i] = db.coerceIn(-12f, 12f) })

    /** `on;ROCK;5,4,…;0` for the preference store. */
    fun encode(): String = listOf(if (enabled) "on" else "off", preset.name, gains.joinToString(","), preampDb.toString()).joinToString(";")

    companion object {
        fun decode(s: String?): EqSettings = runCatching {
            val (on, preset, gains, preamp) = s!!.split(';')
            EqSettings(on == "on", EqPreset.valueOf(preset), gains.split(',').map { it.toFloat().coerceIn(-12f, 12f) }.also { require(it.size == 10) }, preamp.toFloat().coerceIn(-12f, 12f))
        }.getOrDefault(EqSettings())
    }
}
