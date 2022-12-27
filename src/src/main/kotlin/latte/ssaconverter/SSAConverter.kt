package latte.ssaconverter

import latte.Absyn.*
import latte.common.LatteDefinitions
import latte.ssaconverter.ssa.*
import latte.typecheck.unexpectedErrorExit

class SSAConverter(var program: Prog, val definitions: LatteDefinitions) {
    private var ssa = SSA()
    fun convert(): SSA {
        visitProgram()
        return ssa
    }

    private fun visitProgram() {
        for (def in program.listtopdef_) {
            visitTopDef(def)
        }
    }

    private fun visitTopDef(def: TopDef?) {
        unexpectedErrorExit(def == null, "topdef")
        when (def!!) {
            is FnDef -> visitFnDef(def as FnDef)
            is TopClassDef -> TODO("top class def")
            is SubClassDef -> TODO("sub class def")
            else -> TODO("top def")
        }
    }

    private fun visitFnDef(fnDef: FnDef) {
        TODO("Not yet implemented")
    }
}