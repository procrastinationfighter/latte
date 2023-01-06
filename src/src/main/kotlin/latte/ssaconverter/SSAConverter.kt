package latte.ssaconverter

import latte.Absyn.*
import latte.common.LatteDefinitions
import latte.ssaconverter.ssa.*
import latte.ssaconverter.ssa.AddOp
import latte.typecheck.unexpectedErrorExit
import kotlin.system.exitProcess

fun argToType(arg: OpArgument): Type {
    return when (arg) {
        is RegistryArg -> arg.type
        is BoolArg -> Bool()
        is IntArg -> Int()
        is StringArg -> Str()
        else -> TODO("argToType not implemented for $arg")
    }
}

class SSAConverter(var program: Prog, private val definitions: LatteDefinitions) {
    private var ssa = SSA()
    private var nextRegistry = 0
    private var nextLabelNo = 1
    private var currEnv: MutableList<MutableMap<String, OpArgument>> = mutableListOf()
    private var currTypes = mutableMapOf<Int, Type>()
    private var currBlock = SSABlock("", emptyList(), this)

    private fun copyCurrEnv(): List<Map<String, OpArgument>> {
        // TODO: Check if the list is deep copied
        return currEnv.map { it.toMap() }
    }
    private fun getNextLabel(): String {
        val label = "L$nextLabelNo"
        nextLabelNo++
        return label
    }

    private fun resetLabel() {
        nextLabelNo = 1
    }

    private fun resetRegistry() {
        nextRegistry = 0
    }

    private fun getNextRegistry(): Int {
        val prev = nextRegistry
        nextRegistry++
        return prev
    }

    private fun getVarValue(name: String): OpArgument {
        for (env in currEnv.reversed()) {
            val r = env[name]
            if (r != null) {
                return r
            }
        }

        // Technically, shouldn't happen: type checker already checked that
        return RegistryArg(-1, Void())
    }

    private fun restoreEnv(block: SSABlock) {
        currEnv = block.endEnv!!.map { it.toMutableMap() } as MutableList<MutableMap<String, OpArgument>>
    }

    private fun isVarFromThisBlock(name: String): Boolean {
        return currEnv[currEnv.size - 1][name] != null
    }

    private fun setVar(name: String, type: Type, arg: OpArgument) {
        currEnv[currEnv.size - 1][name] = arg

        if (arg is RegistryArg) {
            currTypes[arg.number] = type
        }
    }

    fun changeVar(name: String, arg: OpArgument) {
        for (env in currEnv.reversed()) {
            val r = env[name]
            if (r != null) {
                if (arg is RegistryArg) {
                    currTypes[arg.number] = argToType(r)
                }
                env[name] = arg
                return
            }
        }

        // Technically, shouldn't happen: type checker already checked that
    }

    private fun prepFun() {
        resetRegistry()
        resetLabel()
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
            val reg = getNextRegistry()
            currEnv[currEnv.size - 1][a.ident_] = RegistryArg(reg, a.type_)
            currTypes[reg] = a.type_
        }
    }

    private fun visitBlock(block: Blk): SSABlock {
        val ssaBlock = SSABlock(getNextLabel(), emptyList(), this)
        currBlock = ssaBlock
        currEnv.add(mutableMapOf())
        for (stmt in block.liststmt_) {
            visitStmt(stmt)
        }
        currEnv.removeAt(currEnv.size - 1)

        return ssaBlock
    }

    private fun visitStmt(stmt: Stmt) {
        when(stmt) {
            is Ass -> {
                val res = visitExpr(stmt.expr_)
                changeVar(stmt.ident_, res)

                if (!isVarFromThisBlock(stmt.ident_)) {
                    currBlock.addModifiedVar(stmt.ident_, res)
                }
            }
            is BStmt -> {
                if (stmt.block_ is Blk) {
                    currEnv.add(mutableMapOf())
                    for (s in stmt.block_.liststmt_) {
                        visitStmt(s)
                    }
                    currEnv.removeAt(currEnv.size - 1)
                } else {
                    TODO("unknown Block implementation")
                }
            }
            is Cond -> {
                // Handle if(true) and if(false)
                if (stmt.expr_ is ELitTrue) {
                    visitStmt(stmt.stmt_)
                } else if (stmt.expr_ is ELitFalse) {
                    return
                }
                val block = currBlock

                val cond = visitExpr(stmt.expr_)
                val ifLabel = getNextLabel()
                val continueLabel = getNextLabel()
                block.endEnv = copyCurrEnv()

                val ifBlock = SSABlock(ifLabel, emptyList(), this)
                currBlock = ifBlock
                visitStmt(stmt.stmt_)
                ifBlock.addOp(JumpOp(continueLabel))
                ifBlock.endEnv = copyCurrEnv()

                val phi = getPhi(block, ifBlock)
                val continueBlock = SSABlock(continueLabel, phi, this)
                block.addOp(IfOp(cond, ifLabel, continueLabel))

                // Fix block graph
                block.addNext(ifBlock)
                block.addNext(continueBlock)
                ifBlock.addNext(continueBlock)

                currBlock = continueBlock
            }
            is CondElse -> {
                // Handle if(true) and if (false)
                if (stmt.expr_ is ELitTrue) {
                    visitStmt(stmt.stmt_1)
                } else if (stmt.expr_ is ELitFalse) {
                    visitStmt(stmt.stmt_2)
                }
                val block = currBlock

                val cond = visitExpr(stmt.expr_)
                val ifLabel = getNextLabel()
                val elseLabel = getNextLabel()
                val continueLabel = getNextLabel()
                currBlock.endEnv = copyCurrEnv()

                val ifBlock = SSABlock(ifLabel, emptyList(), this)
                currBlock = ifBlock
                visitStmt(stmt.stmt_1)
                ifBlock.addOp(JumpOp(continueLabel))
                ifBlock.endEnv = copyCurrEnv()
                restoreEnv(block)

                val elseBlock = SSABlock(elseLabel, emptyList(), this)
                currBlock = elseBlock
                visitStmt(stmt.stmt_2)
                elseBlock.addOp(JumpOp(continueLabel))
                elseBlock.endEnv = copyCurrEnv()

                val phi = getPhi(ifBlock, elseBlock)
                val continueBlock = SSABlock(continueLabel, phi, this)
                block.addOp(IfOp(cond, ifLabel, elseLabel))

                // Fix block graph
                block.addNext(ifBlock)
                block.addNext(elseBlock)
                ifBlock.addNext(continueBlock)
                elseBlock.addNext(continueBlock)

                currBlock = continueBlock
            }
            is Decl -> visitListItem(stmt.type_, stmt.listitem_)
            is Decr -> {
                val reg = getNextRegistry()
                val regArg = RegistryArg(reg, Int())
                currBlock.addOp(AddOp(reg, getVarValue(stmt.ident_), IntArg(1), Minus()))
                changeVar(stmt.ident_, regArg)

                if (!isVarFromThisBlock(stmt.ident_)) {
                    currBlock.addModifiedVar(stmt.ident_, regArg)
                }
            }
            is Empty -> {}
            is Incr -> {
                val reg = getNextRegistry()
                val regArg = RegistryArg(reg, Int())
                currBlock.addOp(AddOp(reg, getVarValue(stmt.ident_), IntArg(1), Plus()))
                changeVar(stmt.ident_, regArg)

                if (!isVarFromThisBlock(stmt.ident_)) {
                    currBlock.addModifiedVar(stmt.ident_, regArg)
                }
            }
            is Ret -> {
                val reg = visitExpr(stmt.expr_)
                val type = when (reg) {
                    is IntArg -> Int()
                    is StringArg -> Str()
                    is BoolArg -> Bool()
                    is RegistryArg -> currTypes[reg.number]!!
                    else -> TODO("unknown type in return")
                }
                currBlock.addOp(ReturnOp(type, reg))
            }
            is SExp -> visitExpr(stmt.expr_)
            is VRet -> currBlock.addOp(ReturnVoidOp())
            is While -> visitWhile(stmt)

            is ArrayAss -> TODO("extension: array ass")
            is ClassAss -> TODO("extension: class ass")
            is For -> TODO("extension: for")
            else -> TODO("unknown stmt")
        }
    }

    private fun visitWhile(stmt: While) {
        if (stmt.expr_ is ELitFalse) {
            // don't emit code for while (false)
            return
        }
        // 1. initial block -> 2
        // 2. condition block -> 3 | 4
        // 3. loop body block -> 2
        // 4. continue block

        // Most "brutal" way:
        // go 1 -> 3 -> 2 (calculate phis) -> 3 (calculate phis) -> 4 (calculate phis)
        // potential optimization: traverse firstly 2 and 3 with alternative versions of the functions

        // Labels
        val condLabel = getNextLabel()
        val bodyLabel = getNextLabel()
        val continueLabel = getNextLabel()

        // Visit 3
        currBlock.endEnv = copyCurrEnv()
        val block = currBlock
        val tempReg = nextRegistry
        val dummyBody = SSABlock(bodyLabel, emptyList(), this)
        currBlock = dummyBody
        visitStmt(stmt.stmt_)
        val dummyFinishBody = currBlock
        currBlock.endEnv = copyCurrEnv()
        restoreEnv(block)

        // Condition block
        val condPhi = getPhi(block, dummyFinishBody)
        val condBlock = SSABlock(condLabel, condPhi, this)
        currBlock = condBlock
        val reg = visitExpr(stmt.expr_)
        condBlock.addOp(IfOp(reg, bodyLabel, continueLabel))
        condBlock.endEnv = copyCurrEnv()
        block.addNext(condBlock)
        block.addOp(JumpOp(condLabel))

        // Body block
        // Restore registers so that this time it generates the same registers as for the first time
        val tempReg2 = nextRegistry
        nextRegistry = tempReg
        val bodyBlock = SSABlock(bodyLabel, emptyList(), this)
        currBlock = bodyBlock
        visitStmt(stmt.stmt_)
        val finishBodyBlock = currBlock
        finishBodyBlock.addOp(JumpOp(condLabel))
        finishBodyBlock.addNext(condBlock)
        finishBodyBlock.endEnv = copyCurrEnv()
        nextRegistry = tempReg2

        // Continue block
        restoreEnv(condBlock)
        val continueBlock = SSABlock(continueLabel, emptyList(), this)
        currBlock = continueBlock

        condBlock.addNext(continueBlock)
        condBlock.addNext(bodyBlock)
    }

    private fun getPhi(first: SSABlock, second: SSABlock): List<Phi> {
        val l = mutableListOf<Phi>()

        if (first.endEnv == null) {
            println("first in phi")
            exitProcess(1)
        }
        if (second.endEnv == null) {
            println("second in phi")
            exitProcess(1)
        }
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
                                first.label to firstEnv[pair.key]!!,
                                second.label to secondEnv[pair.key]!!,
                        )))
                }
            }
        }

        return l
    }

    private fun visitListItem(type: Type, listItem: ListItem) {
        for (item in listItem) {
            when (item) {
                is Init -> {
                    val expr = visitExpr(item.expr_)
                    setVar(item.ident_, type, expr)
                }
                is NoInit -> {
                    setVar(item.ident_, type, getTypeDefaultValue(type))
                }
                else -> TODO("unknown item type")
            }
        }
    }

    private fun getTypeDefaultValue(type: Type): OpArgument {
        return when (type) {
            is latte.Absyn.Int -> IntArg(0)
            is Str -> StringArg("empty", 1)
            is Bool -> BoolArg(false)
            else -> TODO("default type not implemented for $type")
        }
    }

    private fun visitExpr(expr: Expr): OpArgument {
        return when (expr) {
            is EOr -> visitOr(expr)
            is EAnd -> {
                val left = visitExpr(expr.expr_1)
                val right = visitExpr(expr.expr_2)
                val reg = getNextRegistry()
                currBlock.addOp(AndOp(reg, left, right))
                currTypes[reg] = Bool()

                return RegistryArg(reg, Bool())
            }
            is ERel -> {
                val left = visitExpr(expr.expr_1)
                val right = visitExpr(expr.expr_2)
                val reg = getNextRegistry()
                val t = argToType(left)
                if (t is Str) {
                    currBlock.addOp(StringRelationOp(reg, left, right, expr.relop_))
                } else {
                    currBlock.addOp(RelationOp(reg, left, right, expr.relop_))
                }
                currTypes[reg] = Bool()

                return RegistryArg(reg, Bool())
            }
            is EAdd -> {
                val left = visitExpr(expr.expr_1)
                val right = visitExpr(expr.expr_2)
                val reg = getNextRegistry()
                var type: Type? = null

                when (left) {
                    is RegistryArg -> {
                        type = currTypes[left.number]
                        if (type == null) {
                            TODO("registry ${left.number} has no type assigned")
                        } else if (type is latte.Absyn.Int) {
                            currBlock.addOp(AddOp(reg, left, right, expr.addop_))
                            currTypes[reg] = Int()
                        } else if (type is Str) {
                            currBlock.addOp(AddStringOp(reg, left, right))
                            currTypes[reg] = Str()
                        }
                    }
                    is IntArg -> {
                        currBlock.addOp(AddOp(reg, left, right, expr.addop_))
                        currTypes[reg] = Int()
                        type = Int()
                    }
                    is StringArg -> {
                        currBlock.addOp(AddStringOp(reg, left, right))
                        currTypes[reg] = Str()
                        type = Str()
                    }
                    else -> TODO("not supported add quadruple op")
                }

                return RegistryArg(reg, type)
            }
            is EMul -> {
                val left = visitExpr(expr.expr_1)
                val right = visitExpr(expr.expr_2)
                val reg = getNextRegistry()
                currBlock.addOp(MultiplicationOp(reg, left, right, expr.mulop_))
                currTypes[reg] = Int()

                return RegistryArg(reg, Int())
            }
            is Not -> {
                val res = visitExpr(expr.expr_)
                val reg = getNextRegistry()
                currBlock.addOp(NotOp(reg, res))
                currTypes[reg] = Bool()
                return RegistryArg(reg, Bool())
            }
            is Neg -> {
                val res = visitExpr(expr.expr_)
                val reg = getNextRegistry()
                currBlock.addOp(NegOp(reg, res))
                currTypes[reg] = Int()
                return RegistryArg(reg, Int())
            }
            is EApp -> {
                val args = visitListExpr(expr.listexpr_)
                val type = definitions.functions[expr.ident_]!!.returnType
                if (type is Void) {
                    // If void, don't assign to a registry
                    currBlock.addOp(AppOp(0, expr.ident_, type, args))
                    return RegistryArg(0, type)
                } else {
                    val reg = getNextRegistry()
                    currBlock.addOp(AppOp(reg, expr.ident_, type, args))
                    currTypes[reg] = type
                    return RegistryArg(reg, type)
                }
            }
            is ELitFalse -> BoolArg(false)
            is ELitTrue -> BoolArg(true)
            is ELitInt -> IntArg(expr.integer_)
            is EString -> StringArg(ssa.addStr(expr.string_), expr.string_.length + 1)
            is EVar -> getVarValue(expr.ident_)

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

    private fun visitOr(expr: EOr): OpArgument {
        val left = visitExpr(expr.expr_1)
        // This must be evaluated lazily. If left was true, then don't evaluate right.
        val trueLabel = getNextLabel()
        val falseLabel = getNextLabel()
        val continueLabel = getNextLabel()
        currBlock.addOp(IfOp(left, trueLabel, falseLabel))

        val trueBlock = SSABlock(trueLabel, emptyList(), this)
        trueBlock.addOp(JumpOp(continueLabel))

        val falseBlock = SSABlock(falseLabel, emptyList(), this)
        currBlock.addNext(trueBlock)
        currBlock.addNext(falseBlock)
        currBlock = falseBlock

        val right = visitExpr(expr.expr_2)
        val reg = getNextRegistry()
        falseBlock.addOp(OrOp(reg, left, right))
        falseBlock.addOp(JumpOp(continueLabel))

        val phiReg = getNextRegistry()
        val phi = Phi("", phiReg, mapOf(trueLabel to BoolArg(true), falseLabel to RegistryArg(reg, Bool())))

        val continueBlock = SSABlock(continueLabel, listOf(phi), this)
        currTypes[phiReg] = Bool()

        trueBlock.addNext(continueBlock)
        falseBlock.addNext(continueBlock)

        currBlock = continueBlock

        return RegistryArg(phiReg, Bool())
    }

    private fun visitListExpr(listexpr_: ListExpr): List<OpArgument> {
        return listexpr_.map { expr -> visitExpr(expr) }
    }
}
