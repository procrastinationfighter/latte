package latte

import org.antlr.v4.runtime.CharStreams

fun main(args: Array<String>) {
    val lexer = latteLexer(CharStreams.fromString("int main() {return 0;}"))
    println("Hello, Latte")
}