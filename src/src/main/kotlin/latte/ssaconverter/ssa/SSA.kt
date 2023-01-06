package latte.ssaconverter.ssa

import latte.Absyn.FnDef

class SSA {

    private var nextStringNo = 1
    var defs = mutableMapOf<String, SSAFun>()
    var strings = mutableMapOf("" to "emptystr")
    fun print() {
        println("static strings:")
        for (s in strings) {
            println("@${s.value} = ${s.key}")
        }
        for (f in defs) {
            f.value.print()
        }
    }

    private fun getNextStringId(): String {
        val str = "str${nextStringNo}"
        nextStringNo++
        return str
    }

    fun addStr(str: String): String {
        val s = strings[str]
        return if (s != null) {
            s
        } else {
            val res = getNextStringId()
            strings[str] = res
            res
        }
    }

    fun addFun(fnDef: FnDef, block: SSABlock) {
        defs[fnDef.ident_] = SSAFun(fnDef.ident_, fnDef.type_, fnDef.listarg_, block)
    }
}
