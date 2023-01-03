package latte.ssaconverter

import latte.Absyn.*
import latte.common.LatteDefinitions
import latte.ssaconverter.ssa.*
import latte.ssaconverter.ssa.AddOp
import latte.typecheck.unexpectedErrorExit

class SSAConverter(var program: Prog, private val definitions: LatteDefinitions) {
    private var ssa = SSA()
    private var nextRegistry = 1
    private var nextLabelNo = 1
    private var currEnv: MutableList<MutableMap<String, Int>> = mutableListOf()
    private var currTypes = mutableMapOf<Int, Type>()

    private fun copyCurrEnv(): List<Map<String, Int>> {
        // TODO: Check if the list is deep copied
        return currEnv.map { it.toMap() }
    }
    private fun getNextLabel(): String {
        val label = "L$nextLabelNo"
        nextLabelNo++
        return label
    }

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

    private fun isVarFromThisBlock(name: String): Boolean {
        return currEnv[currEnv.size - 1][name] != null
    }

    private fun setVar(name: String, type: Type): Int {
        val reg = getNextRegistry()
        currEnv[currEnv.size - 1][name] = reg
        currTypes[reg] = type
        return reg
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
        val ssaBlock = SSABlock(getNextLabel(), emptyList())
        var b = ssaBlock
        currEnv.add(mutableMapOf())
        for (stmt in block.liststmt_) {
            b = visitStmt(stmt, b)
        }
        currEnv.removeAt(currEnv.size - 1)

        return ssaBlock
    }

    private fun visitStmt(stmt: Stmt, block: SSABlock): SSABlock {
        when(stmt) {
            is Ass -> {
                val res = visitExpr(stmt.expr_, block)
                val reg = getNextRegistry()
                block.addOp(AssignOp(reg, res))

                if (isVarFromThisBlock(stmt.ident_)) {
                    block.addModifiedVar(stmt.ident_, reg)
                }
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
            is Cond -> {
                val cond = visitExpr(stmt.expr_, block)
                val ifLabel = getNextLabel()
                val continueLabel = getNextLabel()
                block.endEnv = copyCurrEnv()

                val ifBlock = SSABlock(ifLabel, emptyList())
                visitStmt(stmt.stmt_, ifBlock)
                ifBlock.endEnv = copyCurrEnv()

                val phi = getPhi(block, ifBlock)
                val continueBlock = SSABlock(continueLabel, phi)
                block.addOp(IfOp(cond, ifLabel, continueLabel))

                return continueBlock
            }
            is CondElse -> {
                val cond = visitExpr(stmt.expr_, block)
                val ifLabel = getNextLabel()
                val elseLabel = getNextLabel()
                val continueLabel = getNextLabel()
                block.endEnv = copyCurrEnv()

                val ifBlock = SSABlock(ifLabel, emptyList())
                visitStmt(stmt.stmt_1, ifBlock)
                ifBlock.endEnv = copyCurrEnv()
                currEnv = block.endEnv!!.map { it.toMutableMap() } as MutableList<MutableMap<String, Int>>

                val elseBlock = SSABlock(elseLabel, emptyList())
                visitStmt(stmt.stmt_2, ifBlock)
                ifBlock.endEnv = copyCurrEnv()

                val phi = getPhi(ifBlock, elseBlock)
                val continueBlock = SSABlock(continueLabel, phi)
                block.addOp(IfOp(cond, ifLabel, elseLabel))

                return continueBlock
            }
            is Decl -> visitListItem(stmt.type_, stmt.listitem_, block)
            is Decr -> {
                val reg = getNextRegistry()
                block.addOp(AddOp(reg, RegistryArg(getVarRegistry(stmt.ident_)), IntArg(1), Minus()))

                if (isVarFromThisBlock(stmt.ident_)) {
                    block.addModifiedVar(stmt.ident_, reg)
                }
            }
            is Empty -> {}
            is Incr -> {
                val reg = getNextRegistry()
                block.addOp(AddOp(reg, RegistryArg(getVarRegistry(stmt.ident_)), IntArg(1), Plus()))

                if (isVarFromThisBlock(stmt.ident_)) {
                    block.addModifiedVar(stmt.ident_, reg)
                }
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

        return block
    }

    private fun getPhi(first: SSABlock, second: SSABlock): List<Phi> {
        val l = mutableListOf<Phi>()

        assert(first.endEnv != null)
        assert(second.endEnv != null)
        assert(first.endEnv!!.size == second.endEnv!!.size)

        // In Latte, each block can have up to 2 predecessors.
        // Just iterate which values don't match in both of them.
        for (i in 0 until first.endEnv!!.size) {
            val firstEnv = first.endEnv!![i]
            val secondEnv = second.endEnv!![i]
            for (pair in first.endEnv!![i]) {
                if (firstEnv[pair.key] != secondEnv[pair.key]) {
                    val reg = getNextRegistry()
                    l.add(Phi(pair.key, reg, mapOf(
                                first.label to RegistryArg(firstEnv[pair.key]!!),
                                second.label to RegistryArg(secondEnv[pair.key]!!),
                        )))
                }
            }
        }

        return l
    }


    private fun visitListItem(type: Type, listItem: ListItem, block: SSABlock) {
        for (item in listItem) {
            when (item) {
                is Init -> {
                    val reg = setVar(item.ident_, type)
                    val expr = visitExpr(item.expr_, block)
                    block.addOp(AssignOp(reg, expr))
                }
                is NoInit -> {
                    val reg = setVar(item.ident_, type)
                    block.addOp(AssignOp(reg, getTypeDefaultValue(type)))
                }
                else -> TODO("unknown item type")
            }
        }
    }

    private fun getTypeDefaultValue(type: Type): OpArgument {
        return when (type) {
            is latte.Absyn.Int -> IntArg(0)
            is Str -> StringArg("")
            is Bool -> BoolArg(false)
            else -> TODO("default type not implemented for $type")
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
