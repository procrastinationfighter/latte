package latte.ssaconverter.ssa

import latte.Absyn.ListArg
import latte.Absyn.Type
import latte.common.typeToString
import java.util.*

class SSAFun(val name: String, val type: Type, val args: ListArg, val block: SSABlock) {
    fun print() {
        val ar = args.joinToString(separator = ",").map { it.toString() }
        println("FUN $name, TYPE: ${typeToString(type)}, ARGS: $ar")
        val q: Queue<SSABlock> = LinkedList()
        val visited = mutableSetOf<String>()
        q.add(block)

        while (!q.isEmpty()) {
            q.remove().traversePrint(visited, q)
        }

        println("END FUN\n")
    }
}
