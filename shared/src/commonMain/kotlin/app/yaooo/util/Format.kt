package app.yaooo.util

/** 1234 -> "1.2k", 1_500_000 -> "1.5M". */
fun compact(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 10_000 -> "${n / 1_000}k"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}k"
    else -> n.toString()
}
