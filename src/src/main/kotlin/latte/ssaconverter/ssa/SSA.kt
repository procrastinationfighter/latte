package latte.ssaconverter.ssa

import latte.Absyn.FnDef

class SSA {

    var defs = mutableMapOf<String, SSAFun>()
    fun print() {
        println("TODO: print ssa")
    }

    fun addFun(fnDef: FnDef, block: SSABlock) {
        defs[fnDef.ident_] = SSAFun(fnDef.type_, fnDef.listarg_, block)
    }
}
