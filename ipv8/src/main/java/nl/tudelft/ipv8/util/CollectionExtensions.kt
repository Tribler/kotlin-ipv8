package nl.tudelft.ipv8.util

import kotlin.random.Random

fun <E> Collection<E>.random(maxSampleSize: Int, random: Random? = null): Collection<E> {
    val sampleSize = kotlin.math.min(size, maxSampleSize)
    return if (random == null) {
        shuffled().subList(0, sampleSize)
    } else {
        shuffled(random).subList(0, sampleSize)
    }

}
