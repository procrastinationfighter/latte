package latte.optimizations

import latte.ssaconverter.ssa.OpArgument
import latte.ssaconverter.ssa.SSA
import latte.ssaconverter.ssa.SSABlock
import latte.ssaconverter.ssa.SSAFun

class LCSEConverter(private val ssa: SSA) {

    private val used = mutableSetOf<Int>()
    private val assigned = mutableSetOf<Int>()

    private var toRemove = setOf<Int>()

    // Returns a set of registries that should be removed when dead code is eliminated.
    fun optimize() {
        for (def in ssa.defs) {
            optimizeFun(def.value)
            removeDeadCode(def.value, assigned.subtract(used))

            used.clear()
            assigned.clear()
        }
    }

    private fun removeDeadCode(f: SSAFun, toRemove: Set<Int>) {
        this.toRemove = toRemove
        val visitedBlocks = mutableSetOf(f.block.label)
        visitBlock(f.block, visitedBlocks, ::removeCode)
    }

    private fun removeCode(block: SSABlock) {
        TODO("Not yet implemented")
    }

    private fun optimizeFun(f: SSAFun) {
        val visitedBlocks = mutableSetOf(f.block.label)
        visitBlock(f.block, visitedBlocks, ::reduceCode)
    }

    // Reduces used registries by replacing commonly used.
    // O(n^2), but blocks tend to be small, so this is not a problem.
    private fun reduceCode(ssaBlock: SSABlock) {
        val replaceMap = mutableMapOf<Int, OpArgument>()
        for (i in 0 until ssaBlock.ops.size) {
            val op = ssaBlock.ops[i]
            op.reduce(replaceMap)
            for (j in 0 until i) {
                val otherOp = ssaBlock.ops[j]
                if (op.opEquals(otherOp)) {
                    replaceMap[i] = otherOp.getReplacement()
                }
            }
            op.updateUsed(used)
            op.updateAssigned(assigned)
        }
    }

    private fun visitBlock(block: SSABlock, visitedBlocks: MutableSet<String>, callback: (SSABlock) -> Unit) {
        callback(block)

        for (b in block.next) {
            if (!visitedBlocks.contains(b.label)) {
                visitedBlocks.add(b.label)
                visitBlock(b, visitedBlocks, callback)
            }
        }
    }
}