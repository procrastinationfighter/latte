package latte

import latte.common.LatteException
import latte.topdeffetcher.TopDefFetchingVisitor
import latte.typecheck.TypecheckingVisitor
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import kotlin.system.exitProcess

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
    try {
        val topDefFetcher = TopDefFetchingVisitor()
        val definitions = topDefFetcher.visitStart_Program(tree)!!
        val typechecker = TypecheckingVisitor(definitions)
        typechecker.visitStart_Program(tree)

        System.err.println("good")
        exitProcess(0)
    } catch (e: LatteException) {
        System.err.println("bad")
        System.err.println("semantic error (${e.line},${e.column}): " + e.message)
        exitProcess(1)
    }
}