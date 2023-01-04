package latte.ssaconverter.ssa

import latte.ssaconverter.SSAConverter
import java.util.Queue

class SSABlock(val label: String, phi: List<Phi>, conv: SSAConverter) {
    var ops = mutableListOf<Op>()
    var prev = mutableListOf<SSABlock>()
    var next = mutableListOf<SSABlock>()
    var modifiedVars = mutableMapOf<String, Int>()
    var endEnv: List<Map<String, Int>>? = null

    init {
        for (p in phi) {
            addOp(PhiOp(p))
            conv.changeVar(p.variable, p.registry)
        }
    }

    fun addOp(op: Op) {
        ops.add(op)
    }

    fun addPrev(block: SSABlock) {
        prev.add(block)
    }

    fun addNext(block: SSABlock) {
        next.add(block)
    }

    fun addModifiedVar(name: String, reg: Int) {
        modifiedVars[name] = reg
    }

    fun traversePrint(visited: MutableSet<String>, queue: Queue<SSABlock>) {
        val nextBlocks = next.joinToString(separator = ", ") { it.label }
        println("BLOCK $label, FOLLOWED BY $nextBlocks")
        for (op in ops) {
            op.print()
        }
        println("END BLOCK\n")

        for (n in next) {
            if (!visited.contains(n.label)) {
                visited.add(n.label)
                queue.add(n)
            }
        }
    }
}
