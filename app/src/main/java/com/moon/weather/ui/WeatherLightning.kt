package com.moon.weather.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

internal data class LightningBranch(val points: List<Offset>, val width: Float)

internal data class LightningStrike(
    val sequence: Int,
    val startSeconds: Float,
    val trunk: List<Offset>,
    val branches: List<LightningBranch>,
    val returnDelay: Float?,
    val returnStrength: Float
) {
    val durationSeconds = .9f

    /** A fast return stroke, optional irregular second stroke, then a short afterglow. */
    fun brightnessAt(seconds: Float): Float {
        val age = seconds - startSeconds
        if (age < 0f || age >= durationSeconds) return 0f
        fun pulse(delay: Float, rise: Float, decay: Float): Float {
            val elapsed = age - delay
            return when {
                elapsed < 0f -> 0f
                elapsed < rise -> elapsed / rise
                else -> exp(-(elapsed - rise) / decay)
            }
        }
        return max(pulse(0f, .035f, .105f),
            returnDelay?.let { pulse(it, .02f, .09f) * returnStrength } ?: 0f)
    }
}

/** Retains only the active and next strike. A new session gets a fresh random sequence. */
internal class LightningStorm(private val seed: Int) {
    private var random = Random(seed)
    private var sequence = 0
    private var active: LightningStrike? = null
    private var lastSeconds = 0f
    var nextStrike = createStrike(interval())
        private set

    private fun interval() = 15f + random.nextFloat() * 10f

    private fun createStrike(start: Float): LightningStrike {
        val shape = Random(random.nextInt())
        val origin = Offset(.18f + shape.nextFloat() * .64f, -.025f + shape.nextFloat() * .08f)
        val impact = Offset((origin.x + (shape.nextFloat() - .5f) * .44f).coerceIn(.08f,.92f),
            .395f + shape.nextFloat() * .055f)

        fun channel(from: Offset, to: Offset, roughness: Float, levels: Int): List<Offset> {
            val points = mutableListOf(from)
            fun subdivide(a: Offset, b: Offset, jitter: Float, depth: Int) {
                if (depth == 0) { points += b; return }
                val midpoint = (a + b) / 2f + Offset((shape.nextFloat() - .5f) * jitter,
                    (shape.nextFloat() - .5f) * jitter * .12f)
                subdivide(a,midpoint,jitter*.55f,depth-1)
                subdivide(midpoint,b,jitter*.55f,depth-1)
            }
            subdivide(from,to,roughness,levels)
            return points
        }

        val trunk = channel(origin,impact,.19f,6)
        val branches = mutableListOf<LightningBranch>()
        repeat(4 + shape.nextInt(4)) { index ->
            val fork = trunk[8 + shape.nextInt(43)]
            val side = if (index % 2 == 0) -1f else 1f
            val tip = Offset((fork.x + side * (.08f + shape.nextFloat() * .19f)).coerceIn(.015f,.985f),
                (fork.y + .05f + shape.nextFloat() * .115f).coerceAtMost(.455f))
            val points = channel(fork,tip,.065f,4)
            branches += LightningBranch(points,.45f + shape.nextFloat() * .25f)
            if (shape.nextBoolean()) {
                val twig = points[5 + shape.nextInt(6)]
                val twigTip = Offset((twig.x + side * (.025f + shape.nextFloat() * .06f)).coerceIn(.01f,.99f),
                    (twig.y + .025f + shape.nextFloat() * .045f).coerceAtMost(.47f))
                branches += LightningBranch(channel(twig,twigTip,.025f,3),.25f)
            }
        }
        return LightningStrike(sequence++,start,trunk,branches,
            if (shape.nextFloat() < .7f) .16f + shape.nextFloat() * .10f else null,
            .65f + shape.nextFloat() * .25f)
    }

    fun strikeAt(seconds: Float): LightningStrike? {
        if (seconds < lastSeconds) {
            random = Random(seed); sequence = 0; active = null
            nextStrike = createStrike(interval())
        }
        lastSeconds = seconds
        while (seconds >= nextStrike.startSeconds) {
            active = nextStrike
            nextStrike = createStrike(nextStrike.startSeconds + interval())
        }
        return active?.takeIf { seconds - it.startSeconds < it.durationSeconds }
    }
}

/** Paths and bloom brushes are cached once per strike, rather than rebuilt on every frame. */
internal class LightningRenderer(private val width: Float, private val height: Float, private val density: Float) {
    private data class Channel(val path: Path, val width: Float)
    private var cachedStrike: LightningStrike? = null
    private var channels = emptyList<Channel>()
    private var trunk = emptyList<Offset>()
    private var cloudGlow: Brush? = null
    private val skyWash = Brush.verticalGradient(0f to Color(0xFFDCE5FF).copy(alpha = .46f),
        .45f to Color(0xFFC3D7FF).copy(alpha = .25f), .68f to Color.Transparent,
        startY = 0f,endY = height)

    fun draw(scope: DrawScope, strike: LightningStrike, brightness: Float, opacity: Float) = with(scope) {
        if (brightness <= .001f) return@with
        if (cachedStrike !== strike) {
            cachedStrike = strike
            fun pixels(point: Offset) = Offset(point.x * width,point.y * height)
            fun path(points: List<Offset>) = Path().apply {
                val first = pixels(points.first())
                moveTo(first.x,first.y)
                points.drop(1).forEach { val p = pixels(it); lineTo(p.x,p.y) }
            }
            trunk = strike.trunk.map(::pixels)
            channels = listOf(Channel(path(strike.trunk),1f)) +
                strike.branches.map { Channel(path(it.points),it.width) }
            cloudGlow = Brush.radialGradient(listOf(Color(0xFFE7E9FF).copy(alpha = .60f),
                Color(0xFFB6CFFF).copy(alpha = .16f),Color.Transparent),
                Offset(trunk.first().x,height*.12f),width*.70f)
        }
        val light = (brightness * opacity).coerceIn(0f,1f)
        // Illuminate the surrounding clouds, with the flash falling away toward the foreground.
        drawRect(skyWash,alpha = light)
        drawRect(cloudGlow!!,alpha = light)
        for (channel in channels) {
            val strength = light * if (channel.width == 1f) 1f else .78f
            for ((radius,alpha) in listOf(22f to .025f,14f to .055f,8f to .10f,4f to .24f)) {
                drawPath(channel.path,Color(0xFFB8CEFF).copy(alpha = strength * alpha),
                    style = Stroke(radius * density * channel.width,cap = StrokeCap.Round))
            }
            drawPath(channel.path,Color(0xFFE5EBFF).copy(alpha = strength*.95f),
                style = Stroke(1.6f * density * channel.width,cap = StrokeCap.Round))
        }
        // The hot white core tapers toward the ground; forks stay finer than the main channel.
        trunk.zipWithNext().forEachIndexed { index,(from,to) ->
            val taper = 1f - index.toFloat() / (trunk.size - 1)
            drawLine(Color.White.copy(alpha = light),from,to,
                strokeWidth = (.75f + 1.35f * taper) * density,cap = StrokeCap.Round)
        }
    }
}
