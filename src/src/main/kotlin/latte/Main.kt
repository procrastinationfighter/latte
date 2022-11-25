package latte

import latte.topdeffetcher.TopDefFetchingVisitor
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("No input file provided")
        return
    } else if (!args[0].endsWith(".lat")) {
        System.err.println("Input file must have .lat extension")
        return
    }

    val commonFilename = args[0].substring(0, args[0].length - 4)
    val input = File(args[0]).inputStream()

    val lexer = latteLexer(CharStreams.fromStream(input))
    val parser = latteParser(CommonTokenStream(lexer))

    val tree = parser.start_Program()

    val definitions = tree.accept(TopDefFetchingVisitor())

    if (definitions != null) {
        System.out.println("good")
        System.out.println(definitions.classes.toString())
        System.out.println(definitions.functions.toString())
    } else {
        System.out.println("bad")
    }
}