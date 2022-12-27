package latte.ssaconverter.ssa

class SSABlock {
    var ops = mutableListOf<Op>()
    var prev = mutableListOf<SSABlock>()
    var next = mutableListOf<SSABlock>()

    fun addOp(op: Op) {
        ops.add(op)
    }

    fun addPrev(block: SSABlock) {
        prev.add(block)
    }

    fun addNext(block: SSABlock) {
        next.add(block)
    }
}
