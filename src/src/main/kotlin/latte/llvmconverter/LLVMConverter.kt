package latte.llvmconverter

import latte.Absyn.*
import latte.common.typeToString
import latte.ssaconverter.ssa.ReturnVoidOp
import latte.ssaconverter.ssa.SSA
import latte.ssaconverter.ssa.SSABlock
import latte.ssaconverter.ssa.SSAFun
import java.lang.StringBuilder

fun typeToLlvm(type: Type): String {
    return when(type) {
        is latte.Absyn.Int -> "i32"
        is Str -> "i8*"
        is Bool -> "i1"
        is Void -> "void"
        else -> throw RuntimeException("type not supported in typeToLlvm: ${typeToString(type)}")
    }
}

class LLVMConverter(private val ssa: SSA) {

    private fun getExternalFuns(): String {
        return "declare void @printInt(i32)\n" +
                "declare void @printString(i8*)\n" +
                "declare void @error()\n" +
                "declare i32 @readInt()\n" +
                "declare i8* @readString()\n" +
                "declare i32 @compareStr(i8*, i8*, i32)\n" +
                "declare i8* @addStr(i8*, i8*)"
    }

    private fun strToLlvm(str: String): String {
        val type = "[${str.length + 1} x i8]"

        // We ignore special characters like \n
        return "$type c\"$str\\00\""
    }

    fun convert(): String {
        val strings = ssa.strings.map { "@${it.value} = internal constant ${strToLlvm(it.key)}" }.joinToString(separator="\n")
        val functions = ssa.defs.map { funToStr(it.value) }
        val external = getExternalFuns()
        return functions.joinToString(separator = "\n", prefix = "$external\n$strings\n")
    }

    private fun funToStr(f: SSAFun): String {
        val header = getFunHeader(f)
        val body = getFunBody(f.block, f.type)

        return "$header\n$body\n}\n"
    }

    private fun getFunBody(block: SSABlock, funType: Type): String {
        val visited = mutableSetOf(block.label)
        val blocks = mutableListOf<String>()
        visitBlock(block, visited, blocks, funType)

        return blocks.joinToString(separator = "\n")
    }

    private fun visitBlock(block: SSABlock, visited: MutableSet<String>, blocks: MutableList<String>, funType: Type) {
        val ops = mutableListOf<String>()
        for (op in block.ops) {
            ops.add(op.toLlvm())
        }
        if (ops.isEmpty()) {
            if (funType is Void) {
                ops.add(ReturnVoidOp().toLlvm())
            } else {
                // An empty block that should be skipped.
                return
            }
        } else if (!block.returned && !block.ended) {
            if (funType is Void) {
                ops.add(ReturnVoidOp().toLlvm())
            } else {
                throw RuntimeException("somehow found a block that does not end with return!")
            }
        }

        blocks.add("${block.label}:\n  " + ops.joinToString(separator = "\n  "))

        for (n in block.next) {
            if (!visited.contains(n.label)) {
                visited.add(n.label)
                visitBlock(n, visited, blocks, funType)
            }
        }
    }

    private fun getFunHeader(f: SSAFun): String {
        var i = 0
        val args = f.args.map { arToLlvm(it as Ar, i++) }.joinToString(separator = ", ")

        return "define ${typeToLlvm(f.type)} @${f.name} ($args) {"
    }

    private fun arToLlvm(arg: Ar, reg: Int): String {
        return "${typeToLlvm(arg.type_)} %reg$reg"
    }

}