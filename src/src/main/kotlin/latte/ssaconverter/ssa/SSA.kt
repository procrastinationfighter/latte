package latte.ssaconverter.ssa

import latte.Absyn.Ar
import latte.Absyn.FnDef
import latte.Absyn.ListArg
import java.util.*

class SSA {

    private var nextStringNo = 1
    var defs = mutableMapOf<String, SSAFun>()
    var classDefs = mutableMapOf<String, SSAClass>()
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

    fun addFun(fnDef: FnDef, obj: Optional<Ar>, block: SSABlock, ident: String) {
        val args = if (obj.isPresent) {
            LinkedList(listOf(obj.get())) + fnDef.listarg_
        } else {
            fnDef.listarg_
        }
        defs[fnDef.ident_] = SSAFun(ident, fnDef.type_, args, block)
    }

    fun addClass(name: String, def: SSAClass) {
        classDefs[name] = def
    }
}
