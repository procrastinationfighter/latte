package latte.llvmconverter

import latte.Absyn.*
import latte.common.typeToString
import latte.ssaconverter.ssa.SSA
import latte.ssaconverter.ssa.SSABlock
import latte.ssaconverter.ssa.SSAFun

class LLVMConverter(private val ssa: SSA) {

    private fun getExternalFuns(): String {
        return "declare void @printInt(i32)\n" +
                "declare void @printString(i8*)\n" +
                "declare void @error()\n" +
                "declare i32 @readInt()\n" +
                "declare i8* @readString()\n" +
                "declare i32 @compareStr(i8*, i8*, i32)\n"
    }

    fun convert(): String {
        val functions = ssa.defs.map { funToStr(it.value) }
        val external = getExternalFuns()
        return functions.joinToString(separator = "\n", prefix = external)
    }

    private fun funToStr(f: SSAFun): String {
        val header = getFunHeader(f)
        val body = getFunBody(f.block)

        return "$header\n$body\n}\n"
    }

    private fun getFunBody(block: SSABlock): String {
        val visited = mutableSetOf(block.label)
        val blocks = mutableListOf<String>()
        visitBlock(block, visited, blocks)

        return blocks.joinToString(separator = "\n")
    }

    private fun visitBlock(block: SSABlock, visited: MutableSet<String>, blocks: MutableList<String>) {
        val ops = mutableListOf<String>()
        for (op in block.ops) {
            ops.add(op.toLlvm())
        }

        blocks.add("${block.label}:\n  " + ops.joinToString(separator = "\n  "))

        for (n in block.next) {
            if (!visited.contains(n.label)) {
                visited.add(n.label)
                visitBlock(n, visited, blocks)
            }
        }
    }

    private fun getFunHeader(f: SSAFun): String {
        var i = 1
        val args = f.args.map { arToLlvm(it as Ar, i++) }

        return "define ${typeToLlvm(f.type)} @${f.name} ($args)"
    }

    private fun arToLlvm(arg: Ar, reg: Int): String {
        return "${typeToLlvm(arg.type_)} %$reg"
    }

    private fun typeToLlvm(type: Type): String {
        return when(type) {
            is latte.Absyn.Int -> "i32"
            is Str -> "i8*"
            is Bool -> "i1"
            else -> TODO("type not supported in typeToLlvm: ${typeToString(type)}")
        }
    }

}