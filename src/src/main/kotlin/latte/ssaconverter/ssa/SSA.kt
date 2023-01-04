package latte.ssaconverter.ssa

import latte.Absyn.FnDef

class SSA {

    var defs = mutableMapOf<String, SSAFun>()
    fun print() {
        for (f in defs) {
            f.value.print()
        }
    }

    fun addFun(fnDef: FnDef, block: SSABlock) {
        defs[fnDef.ident_] = SSAFun(fnDef.ident_, fnDef.type_, fnDef.listarg_, block)
    }
}
