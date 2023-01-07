package latte

import latte.Absyn.Prog
import latte.common.LatteException
import latte.llvmconverter.LLVMConverter
import latte.optimizations.LCSEConverter
import latte.parse.ErrorListener
import latte.returnchecker.ReturnCheckerVisitor
import latte.ssaconverter.SSAConverter
import latte.topdeffetcher.TopDefFetchingVisitor
import latte.typecheck.TypecheckingVisitor
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.lang.NumberFormatException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("No input file provided")
        return
    } else if (!args[0].endsWith(".lat")) {
        System.err.println("Input file must have .lat extension")
        return
    } else if (args.size < 2) {
        System.err.println("Directory to runtime.bc not provided")
        return
    } else if (args.size >= 3 && args[2] != "--no-opt") {
        System.err.println("Unknown option: ${args[3]}")
        return
    }

    val input = File(args[0]).inputStream()
    try {
        val errorListener = ErrorListener()

        val lexer = latteLexer(CharStreams.fromStream(input))
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)

        val parser = latteParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val tree = parser.start_Program()

        val topDefFetcher = TopDefFetchingVisitor()
        val definitions = topDefFetcher.visitStart_Program(tree)!!

        val typeChecker = TypecheckingVisitor(definitions)
        typeChecker.visitStart_Program(tree)

        val returnChecker = ReturnCheckerVisitor()
        returnChecker.visitStartProgram(tree)

        val ssaConverter = SSAConverter(tree.result as Prog, definitions)
        val ssa = ssaConverter.convert()

        // Optimize only if flag is specified.
        if (args.size < 3) {
            val lcseConverter = LCSEConverter(ssa)
            lcseConverter.optimize()
        }

        val llvmConverter = LLVMConverter(ssa)
        val llvmCode = llvmConverter.convert()

        System.err.println("OK")

        val commonFilename = args[0].substring(0, args[0].length - 4)
        val llvmOutputFilename = "$commonFilename.ll"
        File(llvmOutputFilename).writeText(llvmCode)
        ProcessBuilder("llvm-as", "-o", "${commonFilename}.bc", llvmOutputFilename).start().waitFor()
        ProcessBuilder("llvm-link", "-o", "${commonFilename}.bc", "${commonFilename}.bc", args[1]).start().waitFor()

        exitProcess(0)
    } catch (e: LatteException) {
        System.err.println("ERROR")
        System.err.println("error at (${e.line},${e.column}): " + e.message)
        exitProcess(1)
    } catch (n: NumberFormatException) {
        System.err.println("An integer literal is too big." +
                "Only literals from -2,147,483,647 to 2,147,483,647 are allowed." +
                "Caught exception: ${n.message}")
    } catch (r: RuntimeException) {
        System.err.println("INTERNAL ERROR: ${r.message}\n ${r.stackTraceToString()}")
        exitProcess(1)
    }
}
