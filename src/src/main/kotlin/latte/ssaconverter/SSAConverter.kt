package latte.ssaconverter

import latte.Absyn.*
import latte.common.LatteDefinitions
import latte.ssaconverter.ssa.*
import latte.typecheck.unexpectedErrorExit

class SSAConverter(var program: Prog, val definitions: LatteDefinitions) {
    private var ssa = SSA()
    private var nextRegistry = 1
    private var currEnv: MutableList<MutableMap<String, Int>> = mutableListOf()
    private var currTypes = mutableMapOf<Int, Type>()

    private fun resetRegistry() {
        nextRegistry = 1
    }

    private fun getNextRegistry(): Int {
        val prev = nextRegistry
        nextRegistry++
        return prev
    }

    private fun getVarRegistry(name: String): Int {
        for (env in currEnv.reversed()) {
            val r = env[name]
            if (r != null) {
                return r
            }
        }

        // Technically, shouldn't happen: type checker already checked that
        return -1
    }

    private fun setVar(name: String, type: Type) {
        val reg = getNextRegistry()
        currEnv[currEnv.size - 1][name] = reg
        currTypes[reg] = type
    }

    private fun prepFun() {
        resetRegistry()
        currEnv.clear()
        currEnv.add(mutableMapOf())
        currTypes.clear()
    }

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
            is TopClassDef -> TODO("extension: top class def")
            is SubClassDef -> TODO("extension: sub class def")
        }
    }

    private fun visitFnDef(fnDef: FnDef) {
        prepFun()
        visitArgs(fnDef.listarg_)
        val block = visitBlock(fnDef.block_ as Blk)
        ssa.addFun(fnDef, block)
    }

    private fun visitArgs(args: ListArg) {
        for (arg in args) {
            val a = arg as Ar
            setVar(a.ident_, a.type_)
        }
    }

    private fun visitBlock(block: Blk): SSABlock {
        val ssaBlock = SSABlock()
        for (stmt in block.liststmt_) {
            visitStmt(stmt, ssaBlock)
        }

        return ssaBlock
    }

    private fun visitStmt(stmt: Stmt, block: SSABlock) {
        when(stmt) {
            is ArrayAss -> TODO("extension: array ass")
            is Ass -> TODO("ass")
            is BStmt -> TODO("bstmt")
            is ClassAss -> TODO("extension: class ass")
            is Cond -> TODO("if")
            is CondElse -> TODO("if else")
            is Decl -> TODO("decl")
            is Decr -> TODO("decr")
            is Empty -> {}
            is Incr -> TODO("incr")
            is Ret -> TODO("return")
            is SExp -> TODO("stmt expr")
            is VRet -> TODO("void ret")
            is While -> TODO("while")
            is For -> TODO("extension: for")
        }
    }
}
