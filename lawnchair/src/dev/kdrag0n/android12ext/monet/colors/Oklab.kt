package dev.kdrag0n.android12ext.monet.colors

data class Oklab(
    override val L: Double,
    override val a: Double,
    override val b: Double,
) : Color, Lab {
    override fun toLinearSrgb(): LinearSrgb {
        val l2 = L + 0.3963377774 * a + 0.2158037573 * b
        val m2 = L - 0.1055613458 * a - 0.0638541728 * b
        val s2 = L - 0.0894841775 * a - 1.2914855480 * b

        val l = l2 * l2 * l2
        val m = m2 * m2 * m2
        val s = s2 * s2 * s2

        return LinearSrgb(
            r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    companion object {
        fun LinearSrgb.toOklab(): Oklab {
            val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
            val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
            val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b

            val l2 = Math.cbrt(l)
            val m2 = Math.cbrt(m)
            val s2 = Math.cbrt(s)

            return Oklab(
                L = 0.2104542553 * l2 + 0.7936177850 * m2 - 0.0040720468 * s2,
                a = 1.9779984951 * l2 - 2.4285922050 * m2 + 0.4505937099 * s2,
                b = 0.0259040371 * l2 + 0.7827717662 * m2 - 0.8086757660 * s2,
            )
        }
    }
}
