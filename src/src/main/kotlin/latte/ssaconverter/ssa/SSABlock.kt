package latte.ssaconverter.ssa

import latte.ssaconverter.SSAConverter
import java.util.Queue

class SSABlock(val label: String, phi: List<Phi>, conv: SSAConverter) {
    var returned = false
    var ended = false
    var ops = mutableListOf<Op>()
    var prev = mutableListOf<SSABlock>()
    var next = mutableListOf<SSABlock>()
    var modifiedVars = mutableMapOf<String, OpArgument>()
    var endEnv: List<Map<String, OpArgument>>? = null

    init {
        for (p in phi) {
            addOp(PhiOp(p))
            conv.changeVar(p.variable, RegistryArg(p.registry))
        }
    }

    fun addOp(op: Op) {
        if (returned || ended) {
            return
        }

        if (op is ReturnOp || op is ReturnVoidOp) {
            returned = true
        } else if (op is JumpOp || op is IfOp) {
            ended = true
        }
        ops.add(op)
    }

    fun addPrev(block: SSABlock) {
        prev.add(block)
    }

    fun addNext(block: SSABlock) {
        if (!returned) {
            next.add(block)
        }
    }

    fun addModifiedVar(name: String, arg: OpArgument) {
        modifiedVars[name] = arg
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
