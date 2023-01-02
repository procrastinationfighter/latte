package latte.ssaconverter

import latte.Absyn.*
import latte.common.LatteDefinitions
import latte.ssaconverter.ssa.*
import latte.ssaconverter.ssa.AddOp
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
        currEnv.add(mutableMapOf())
        for (stmt in block.liststmt_) {
            visitStmt(stmt, ssaBlock)
        }
        currEnv.removeAt(currEnv.size - 1)

        return ssaBlock
    }

    private fun visitStmt(stmt: Stmt, block: SSABlock) {
        when(stmt) {
            is Ass -> {
                TODO("assign statement - wait for implementing phi")
            }
            is BStmt -> {
                if (stmt.block_ is Blk) {
                    currEnv.add(mutableMapOf())
                    for (s in stmt.block_.liststmt_) {
                        visitStmt(s, block)
                    }
                    currEnv.removeAt(currEnv.size - 1)
                } else {
                    TODO("unknown Block implementation")
                }
            }
            is Cond -> TODO("if")
            is CondElse -> TODO("if else")
            is Decl -> TODO("decl")
            is Decr -> {
                block.addOp(AddOp(getNextRegistry(), RegistryArg(getVarRegistry(stmt.ident_)), IntArg(1), Minus()))
                TODO("decr not implemented as reassign")
            }
            is Empty -> {}
            is Incr -> {
                block.addOp(AddOp(getNextRegistry(), RegistryArg(getVarRegistry(stmt.ident_)), IntArg(1), Plus()))
                TODO("incr not implemented as reassign")
            }
            is Ret -> {
                val reg = visitExpr(stmt.expr_, block)
                val type = when (reg) {
                    is IntArg -> Int()
                    is StringArg -> Str()
                    is BoolArg -> Bool()
                    is RegistryArg -> currTypes[reg.number]!!
                    else -> TODO("unknown type in return")
                }
                block.addOp(ReturnOp(type, reg))
            }
            is SExp -> visitExpr(stmt.expr_, block)
            is VRet -> block.addOp(ReturnVoidOp())
            is While -> TODO("while")

            is ArrayAss -> TODO("extension: array ass")
            is ClassAss -> TODO("extension: class ass")
            is For -> TODO("extension: for")
            else -> TODO("unknown stmt")
        }
    }

    private fun visitExpr(expr: Expr, block: SSABlock): OpArgument {
        return when (expr) {
            is EOr -> {
                val left = visitExpr(expr.expr_1, block)
                val right = visitExpr(expr.expr_2, block)
                val reg = getNextRegistry()
                block.addOp(OrOp(reg, left, right))

                return RegistryArg(reg)
            }
            is EAnd -> {
                val left = visitExpr(expr.expr_1, block)
                val right = visitExpr(expr.expr_2, block)
                val reg = getNextRegistry()
                block.addOp(AndOp(reg, left, right))

                return RegistryArg(reg)
            }
            is ERel -> {
                // TODO: comparing strings and bools might require separate quadruple op
                val left = visitExpr(expr.expr_1, block)
                val right = visitExpr(expr.expr_2, block)
                val reg = getNextRegistry()
                block.addOp(RelationOp(reg, left, right, expr.relop_))

                return RegistryArg(reg)
            }
            is EAdd -> {
                val left = visitExpr(expr.expr_1, block)
                val right = visitExpr(expr.expr_2, block)
                val reg = getNextRegistry()

                when (left) {
                    is RegistryArg -> {
                        val type = currTypes[left.number]
                        if (type == null) {
                            TODO("registry ${left.number} has no type assigned")
                        } else if (type is latte.Absyn.Int) {
                            block.addOp(AddOp(reg, left, right, expr.addop_))
                        } else if (type is Str) {
                            block.addOp(AddStringOp(reg, left, right))
                        }
                    }
                    is IntArg -> {
                        block.addOp(AddOp(reg, left, right, expr.addop_))
                    }
                    is StringArg -> {
                        block.addOp(AddStringOp(reg, left, right))
                    }
                    else -> TODO("not supported add quadruple op")
                }

                return RegistryArg(reg)
            }
            is EMul -> {
                val left = visitExpr(expr.expr_1, block)
                val right = visitExpr(expr.expr_2, block)
                val reg = getNextRegistry()
                block.addOp(MultiplicationOp(reg, left, right, expr.mulop_))

                return RegistryArg(reg)
            }
            is Not -> {
                val res = visitExpr(expr.expr_, block)
                val reg = getNextRegistry()
                block.addOp(NotOp(reg, res))
                return RegistryArg(reg)
            }
            is Neg -> {
                val res = visitExpr(expr.expr_, block)
                val reg = getNextRegistry()
                block.addOp(NegOp(reg, res))
                return RegistryArg(reg)
            }
            is EApp -> {
                val args = visitListExpr(expr.listexpr_, block)
                val reg = getNextRegistry()
                val type = definitions.functions[expr.ident_]!!.returnType
                block.addOp(AppOp(reg, type, args))
                return RegistryArg(reg)
            }
            is ELitFalse -> BoolArg(false)
            is ELitTrue -> BoolArg(true)
            is ELitInt -> IntArg(expr.integer_)
            is EString -> StringArg(expr.string_)
            is EVar -> RegistryArg(getVarRegistry(expr.ident_))

            is ENull -> TODO("extension: lit null")
            is ENewArr -> TODO("extension: lit new arr")
            is ENewObj -> TODO("extension: lit new obj")
            is EArray -> TODO("extension: array")
            is EClassCall -> TODO("extension: class call")
            is EClassVal -> TODO("extension: class val")
            is ECast -> TODO("extension: cast")
            else -> TODO("unknown expr")
        }
    }

    private fun visitListExpr(listexpr_: ListExpr, block: SSABlock): List<OpArgument> {
        return listexpr_.map { expr -> visitExpr(expr, block) }
    }
}
