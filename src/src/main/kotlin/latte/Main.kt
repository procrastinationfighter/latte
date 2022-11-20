package latte

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File

fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("No input file provided")
        return
    } else if (!args[0].endsWith(".ins")) {
        System.err.println("Input file must have .ins extension")
        return
    }

    val commonFilename = args[0].substring(0, args[0].length - 4)
    val input = File(args[0]).inputStream()

    val lexer = latteLexer(CharStreams.fromStream(input))
    val parser = latteParser(CommonTokenStream(lexer))
    
}