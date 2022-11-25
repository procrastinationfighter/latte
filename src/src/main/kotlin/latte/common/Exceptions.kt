package latte.common

class LatteException(s: String, public val line: Int, public val column: Int) : RuntimeException(s) {
}
