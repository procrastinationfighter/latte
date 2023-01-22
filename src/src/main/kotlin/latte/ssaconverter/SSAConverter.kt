package latte.ssaconverter

import latte.Absyn.*
import latte.common.FuncDef
import latte.common.LatteDefinitions
import latte.common.typeToString
import latte.ssaconverter.ssa.*
import latte.ssaconverter.ssa.AddOp
import latte.typecheck.unexpectedErrorExit
import java.util.*
import kotlin.system.exitProcess

fun argToType(arg: OpArgument): Type {
    return when (arg) {
        is RegistryArg -> arg.type
        is BoolArg -> Bool()
        is IntArg -> Int()
        is StringArg -> Str()
        is NullArg -> Class("")
        else -> TODO("argToType not implemented for $arg")
    }
}

fun getTypeDefaultValue(type: Type): OpArgument {
    return when (type) {
        is latte.Absyn.Int -> IntArg(0)
        is Str -> StringArg("emptystr", 1)
        is Bool -> BoolArg(false)
        is Class -> NullArg(type.ident_)
        else -> TODO("default type not implemented for $type")
    }
}

fun getMemberVariables(definitions: LatteDefinitions, className: String): List<Triple<String, Type, Int>> {
    val def = definitions.classes[className]!!
    val thisVars = mutableListOf<Triple<String, Type, Int>>()
    for (v in def.variables) {
        thisVars.add(Triple(v.key, v.value, def.fieldsOrder[v.key]!!))
    }

    return if (def.parent.isPresent) {
        getMemberVariables(definitions, def.parent.get()) + thisVars
    } else {
        thisVars
    }
}

class SSAConverter(var program: Prog, private val definitions: LatteDefinitions) {
    private var ssa = SSA()
    private var nextRegistry = 0
    private var nextLabelNo = 1
    private var currEnv: MutableList<MutableMap<String, OpArgument>> = mutableListOf()
    private var currTypes = mutableMapOf<Int, Type>()
    private var currBlock = SSABlock("", emptyList(), this)
    private var currClass = Optional.empty<String>()
    private var currReturn = Optional.empty<Type>()

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
        currReturn = Optional.empty()
    }

    fun convert(): SSA {
        visitProgram()
        return ssa
    }

    private fun visitProgram() {
        // Use queue to make sure that parent classes are visited before subclasses
        val q: Queue<TopDef> = LinkedList()
        for (def in program.listtopdef_) {
            q.add(def)
        }

        while (q.isNotEmpty()) {
            val def = q.poll()
            if (def is SubClassDef) {
                if (!ssa.classDefs.contains(def.ident_2)) {
                    q.add(def)
                    continue
                }
            }

            visitTopDef(def)
        }
    }

    private fun visitTopDef(def: TopDef?) {
        unexpectedErrorExit(def == null, "topdef")
        when (def!!) {
            is FnDef -> visitFnDef(def as FnDef)
            is TopClassDef -> visitTopClass(def as TopClassDef)
            is SubClassDef -> visitSubClass(def as SubClassDef)
        }
    }

    private fun visitListClassDef(className: String, defs: ListClassDef): Map<String, Type> {
        val memberVariables = mutableMapOf<String, Type>()
        val methods = mutableListOf<Pair<String, String>>()

        for (d in defs) {
            when (d) {
                is ClassVarDef -> {
                    memberVariables[d.ident_] = d.type_
                }
                is ClassTopDef -> {
                    val top = d.topdef_ as FnDef
                    methods.add(Pair(top.ident_, visitMethod(className, top)))
                }
            }
        }

        return memberVariables.toMap()
    }

    private fun visitMethod(className: String, def: FnDef): String {
        prepFun()
        val classType = Class(className)
        val classDef = definitions.classes[className]!!
        // Steps:
        // 2. add env with args
        val reg = getNextRegistry()
        currTypes[reg] = classType
        currEnv.add(mutableMapOf("self" to RegistryArg(reg, classType)))
        visitArgs(def.listarg_)

        currEnv.add(mutableMapOf())
        currBlock = SSABlock(getNextLabel(), emptyList(), this)
        var objRegistry = 0

        // 3. handle virtuals
        // TODO: add bitcasting and change argument
        val method = classDef.getMethod(def.ident_)
        if (method.second != method.third) {
            // If method is virtual, cast
            objRegistry = getNextRegistry()
            currBlock.addOp(ClassCastOp(objRegistry, method.third, RegistryArg(0, Class(method.second))))
        }

        // 1. add env with member variables
        val memberVariables = getMemberVariables(definitions, className)
        for (v in memberVariables) {
            val classReg = getNextRegistry()
            val valType = v.second
            val order = v.third
            val valReg = getNextRegistry()

            currBlock.addOp(GetClassVarOp(classReg, classType.ident_, RegistryArg(objRegistry, classType), order, valType))
            currBlock.addOp(LoadClassVarOp(valReg, valType, classReg))

            setVar(v.first, v.second, RegistryArg(valReg, v.second))
        }
        // Change places of envs
        val swap = currEnv[currEnv.size - 2]
        currEnv[currEnv.size - 2] = currEnv[currEnv.size - 1]
        currEnv[currEnv.size - 1] = swap

        // 4. visit method body
        currEnv.add(mutableMapOf())
        currReturn = Optional.of(def.type_)
        val methodFirstBlock = currBlock
        for (stmt in (def.block_ as Blk).liststmt_) {
            visitStmt(stmt)
        }

        val selfClass = if (method.second == method.third) {
            Class(className)
        } else {
            Class(method.second)
        }

        ssa.addFun(def, Optional.of(Ar(selfClass, "self")), methodFirstBlock, "${className}.${def.ident_}")

        // TODO: return name of respective class, not self
        return className
    }

    private fun visitSubClass(def: SubClassDef) {
        currClass = Optional.of(def.ident_1)
        val memberVariables = visitListClassDef(def.ident_1, def.listclassdef_)
        currClass = Optional.empty()
        val d = definitions.classes[def.ident_1]!!
        val parent = ssa.classDefs[def.ident_2] ?: throw RuntimeException("class ${def.ident_2} not found as parent class of ${def.ident_1}")
        ssa.addClass(def.ident_1, SSAClass(def.ident_1, memberVariables, Optional.of(parent), d.typesStr, d.fieldsOrder, d.methodsList))
    }

    private fun visitTopClass(def: TopClassDef) {
        currClass = Optional.of(def.ident_)
        val memberVariables = visitListClassDef(def.ident_, def.listclassdef_)
        currClass = Optional.empty()
        val d = definitions.classes[def.ident_]!!
        ssa.addClass(def.ident_, SSAClass(def.ident_, memberVariables, Optional.empty(), d.typesStr, d.fieldsOrder, d.methodsList))
    }

    private fun visitFnDef(fnDef: FnDef) {
        prepFun()
        visitArgs(fnDef.listarg_)
        currReturn = Optional.of(fnDef.type_)
        val block = visitBlock(fnDef.block_ as Blk)
        ssa.addFun(fnDef, Optional.empty(), block, fnDef.ident_)
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

    // Checks if right is a subclass of left, exclusively
    private fun isSubclass(left: String, right: String): Boolean {
        if (left == right) {
            return false
        }
        var def = definitions.classes[right]
        var currName = right
        while (def!!.parent.isPresent) {
            if (currName == left) {
                return true
            } else {
                currName = def.parent.get()
                def = definitions.classes[currName]
            }
        }

        if (currName == left) {
            return true
        }

        return false
    }

    private fun visitStmt(stmt: Stmt) {
        when(stmt) {
            is Ass -> {
                if (memberVariableVisited(stmt.ident_)) {
                    // TODO: adjust register if cast occured
                    val regVal = getNextRegistry()
                    var reg2 = visitExpr(stmt.expr_)

                    val varType = getClassVarType(currClass.get(), stmt.ident_)
                    val varOrder = getFieldOrder(currClass.get(), stmt.ident_)

                    // if casting, use bitcast
                    if (argToType(reg2) is Class && isSubclass((varType as Class).ident_, (argToType(reg2) as Class).ident_ )) {
                        val r = getNextRegistry()
                        currBlock.addOp(ClassCastOp(r, varType.ident_, reg2))
                        reg2 = RegistryArg(r, Class(varType.ident_))
                    }

                    currBlock.addOp(GetClassVarOp(regVal, currClass.get(), RegistryArg(0, Class(currClass.get())), varOrder, varType))
                    currBlock.addOp(StoreOp(varType, reg2, regVal))
                    return
                }
                var res = visitExpr(stmt.expr_)
                val resType = argToType(res)

                // if casting, use bitcast
                if (resType is Class) {
                    val currVal = getVarValue(stmt.ident_)
                    val varType = if (currVal is RegistryArg) {
                        currTypes[currVal.number]!! as Class
                    } else {
                        // Must be null
                        Class((currVal as NullArg).type)
                    }

                    if (isSubclass(varType.ident_, resType.ident_)) {
                        val r = getNextRegistry()
                        currBlock.addOp(ClassCastOp(r, varType.ident_, res))
                        res = RegistryArg(r, Class(varType.ident_))
                    }
                }

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
                    throw RuntimeException("unknown Block implementation")
                }
            }
            is Cond -> {
                // Handle if(true) and if(false)
                if (stmt.expr_ is ELitTrue) {
                    visitStmt(stmt.stmt_)
                    return
                } else if (stmt.expr_ is ELitFalse) {
                    return
                }

                val cond = visitExpr(stmt.expr_)
                val block = currBlock
                val ifLabel = getNextLabel()
                val continueLabel = getNextLabel()
                block.endEnv = copyCurrEnv()

                val ifBlock = SSABlock(ifLabel, emptyList(), this)
                currBlock = ifBlock
                visitStmt(stmt.stmt_)
                val ifEndingBlock = currBlock
                currBlock.addOp(JumpOp(continueLabel))
                currBlock.endEnv = copyCurrEnv()

                val phi = getPhi(block, ifEndingBlock)
                val continueBlock = SSABlock(continueLabel, phi, this)
                block.addOp(IfOp(cond, ifLabel, continueLabel))

                // Fix block graph
                block.addNext(ifBlock)
                block.addNext(continueBlock)
                ifEndingBlock.addNext(continueBlock)

                currBlock = continueBlock
            }
            is CondElse -> {
                // Handle if(true) and if (false)
                if (stmt.expr_ is ELitTrue) {
                    visitStmt(stmt.stmt_1)
                    return
                } else if (stmt.expr_ is ELitFalse) {
                    visitStmt(stmt.stmt_2)
                    return
                }

                val cond = visitExpr(stmt.expr_)
                val block = currBlock
                val ifLabel = getNextLabel()
                val elseLabel = getNextLabel()
                val continueLabel = getNextLabel()
                currBlock.endEnv = copyCurrEnv()

                val ifBlock = SSABlock(ifLabel, emptyList(), this)
                currBlock = ifBlock
                visitStmt(stmt.stmt_1)
                currBlock.endEnv = copyCurrEnv()
                currBlock.addOp(JumpOp(continueLabel))
                val ifEndingBlock = currBlock
                restoreEnv(block)

                val elseBlock = SSABlock(elseLabel, emptyList(), this)
                currBlock = elseBlock
                visitStmt(stmt.stmt_2)
                currBlock.endEnv = copyCurrEnv()
                currBlock.addOp(JumpOp(continueLabel))
                val elseEndingBlock = currBlock

                val phi = getPhi(ifEndingBlock, elseEndingBlock)
                val continueBlock = SSABlock(continueLabel, phi, this)
                block.addOp(IfOp(cond, ifLabel, elseLabel))

                // Fix block graph
                block.addNext(ifBlock)
                block.addNext(elseBlock)
                ifEndingBlock.addNext(continueBlock)
                elseEndingBlock.addNext(continueBlock)

                currBlock = continueBlock
            }
            is Decl -> visitListItem(stmt.type_, stmt.listitem_)
            is Decr -> {
                visitStmt(Ass(stmt.ident_, EAdd(EVar(stmt.ident_), Minus(), ELitInt(1))))
            }
            is Empty -> {}
            is Incr -> {
                visitStmt(Ass(stmt.ident_, EAdd(EVar(stmt.ident_), Plus(), ELitInt(1))))
            }
            is Ret -> {
                var reg = visitExpr(stmt.expr_)
                var type = when (reg) {
                    is IntArg -> Int()
                    is StringArg -> Str()
                    is BoolArg -> Bool()
                    is RegistryArg -> reg.type
                    else -> throw RuntimeException("unknown type in return")
                }

                // If returning a class, cast first.
                if (type is Class) {
                    val ret = currReturn.get() as Class
                    if (ret.ident_ != type.ident_) {
                        val newReg = getNextRegistry()

                        currBlock.addOp(ClassCastOp(newReg, ret.ident_, reg))
                        type = ret
                        reg = RegistryArg(newReg, type)
                    }
                }

                currBlock.addOp(ReturnOp(type, reg))
            }
            is SExp -> visitExpr(stmt.expr_)
            is VRet -> currBlock.addOp(ReturnVoidOp())
            is While -> visitWhile(stmt)

            is ArrayAss -> TODO("extension: array ass")
            is ClassAss -> {
                val regVal = getNextRegistry()
                val reg1 = visitExpr(stmt.expr_1)
                val classType = argToType(reg1)
                if (classType !is Class) {
                    throw RuntimeException("expected class in assignment, found ${typeToString(classType)}")
                }

                val reg2 = visitExpr(stmt.expr_2)
                val varType = getClassVarType(classType.ident_, stmt.ident_)
                val varOrder = getFieldOrder(classType.ident_, stmt.ident_)

                currBlock.addOp(GetClassVarOp(regVal, classType.ident_, reg1, varOrder, varType))
                currBlock.addOp(StoreOp(varType, reg2, regVal))
            }
            is For -> TODO("extension: for")
            else -> TODO("unknown stmt")
        }
    }

    private fun memberVariableVisited(name: String): Boolean {
        var i = currEnv.size - 1
        for (env in currEnv.reversed()) {
            val r = env[name]
            if (r != null) {
                break
            }
            i--
        }

        return i == 1 && currClass.isPresent
    }

    private fun getClassVarType(classIdent: String, varIdent: String): Type {
        val def = definitions.classes[classIdent]!!
        val t = def.variables[varIdent]
        return t
            ?: if (def.parent.isPresent) {
                getClassVarType(def.parent.get(), varIdent)
            } else {
                throw RuntimeException("variable $varIdent not found in class $classIdent")
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

        // fix for phi: run that two times
        // first one estabilishes where to look at
        // second one fixes

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
        currBlock.addOp(IfOp(reg, bodyLabel, continueLabel))
        currBlock.endEnv = copyCurrEnv()
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
        finishBodyBlock.endEnv = copyCurrEnv()

        // Condition block, second try
        // Update phis
        restoreEnv(block)
        val condPhi2 = getPhi(block, finishBodyBlock)
        val condBlock2 = SSABlock(condLabel, condPhi2, this)
        currBlock = condBlock2
        val reg2 = visitExpr(stmt.expr_)
        currBlock.addOp(IfOp(reg2, bodyLabel, continueLabel))
        currBlock.endEnv = copyCurrEnv()
        val finishCondBlock = currBlock
        block.addNext(condBlock2)
        block.addOp(JumpOp(condLabel))
        block.addNext(condBlock2)
        finishBodyBlock.addNext(condBlock2)

        // Continue block
        nextRegistry = tempReg2
        restoreEnv(finishCondBlock)
        val continueBlock = SSABlock(continueLabel, emptyList(), this)
        currBlock = continueBlock

        finishCondBlock.addNext(continueBlock)
        finishCondBlock.addNext(bodyBlock)
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
                    var expr = visitExpr(item.expr_)

                    if (expr is RegistryArg && type is Class) {
                        val exprClass = (expr.type as Class).ident_

                        if (isSubclass(type.ident_, exprClass)) {
                            val r = getNextRegistry()
                            currBlock.addOp(ClassCastOp(r, type.ident_, expr))
                            expr = RegistryArg(r, Class(type.ident_))
                        }
                    }

                    setVar(item.ident_, type, expr)
                }
                is NoInit -> {
                    setVar(item.ident_, type, getTypeDefaultValue(type))

                    val e = when (type) {
                        is latte.Absyn.Int -> ELitInt(0)
                        is Str -> EString("emptystr")
                        is Bool -> ELitFalse()
                        is Class -> ECast(type.ident_, ENull())
                        else -> TODO("default type not implemented for $type")
                    }

                    visitStmt(Ass(item.ident_, e))
                }
                else -> TODO("unknown item type")
            }
        }
    }

    private fun visitExpr(expr: Expr): OpArgument {
        return when (expr) {
            is EOr -> visitOrAnd(expr.expr_1, expr.expr_2, true)
            is EAnd -> visitOrAnd(expr.expr_1, expr.expr_2, false)
            is ERel -> {
                val left = visitExpr(expr.expr_1)
                var right = visitExpr(expr.expr_2)
                val reg = getNextRegistry()
                val t = argToType(left)
                if (t is Str) {
                    currBlock.addOp(StringRelationOp(reg, left, right, expr.relop_))
                } else {
                    if (t is Class) {
                        if (t.ident_ != (argToType(right) as Class).ident_) {
                            // If classes not equal, just cast one to another and compare the pointers
                            val newReg = getNextRegistry()
                            currBlock.addOp(ClassCastOp(newReg, t.ident_, right))
                            right = RegistryArg(newReg, Class(t.ident_))
                        }
                    }
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
                val funDef = definitions.functions[expr.ident_]!!
                val args = visitListExpr(expr.listexpr_, funDef.args)
                val type = funDef.returnType
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

            is ENull -> return NullArg("")
            is ENewArr -> TODO("extension: lit new arr")
            is ENewObj -> {
                if (expr.type_ !is Class) {
                    throw RuntimeException("only classes can be instantiated with new without [], got ${typeToString(expr.type_)}")
                }

                // get class size
                val classSize = getNextRegistry()
                currBlock.addOp(GetClassSizeOp(expr.type_.ident_, classSize, getNextRegistry()))
                // allocate
                val newReg = getNextRegistry()
                currBlock.addOp(AllocateOp(newReg, RegistryArg(classSize, Int()), expr.type_.ident_))
                // initialize
                currBlock.addOp(ZeroInitClassOp(newReg, expr.type_.ident_))

                currTypes[newReg] = expr.type_

                RegistryArg(newReg, expr.type_)
            }
            is EArray -> TODO("extension: array")
            is EClassCall -> {
                var obj = visitExpr(expr.expr_) as RegistryArg
                val c = argToType(obj) as Class
                val method = getMethod(c.ident_, expr.ident_)
                val methodName = "${method.second}.${expr.ident_}"
                val type = method.first.returnType
                // If this class is not equal to the class the method comes from...
                if (c.ident_ != method.second) {
                    // Calling an inherited function

                    val r = getNextRegistry()
                    currBlock.addOp(ClassCastOp(r, method.second, obj))
                    obj = RegistryArg(r, Class(method.second))
                }

                val args = listOf(obj) + visitListExpr(expr.listexpr_, method.first.args)

                val vtableAddrReg = getNextRegistry()

                // Get vtable address
                currBlock.addOp(GetClassVarOp(vtableAddrReg, method.second, obj, 0, Class("Vtable.${method.second}")))
                // Get vtable
                val vtableReg = getNextRegistry()
                currBlock.addOp(LoadClassVarOp(vtableReg, Class("Vtable.${method.second}"), vtableAddrReg))
                // Get method address from vtable
                val methodAddrReg = getNextRegistry()
                currBlock.addOp(
                    GetClassVarOp(
                        methodAddrReg,
                        "Vtable.${method.second}",
                        RegistryArg(vtableReg, Class("Vtable.${method.second}")),
                        method.third,
                        Class("Vtable.${method.second}"))
                )
                // Get method from method address
                val methodReg = getNextRegistry()
                currBlock.addOp(LoadMethodOp(methodReg, method.second, method.first, methodAddrReg))


                if (type is Void) {
                    // If void, don't assign to a registry
                    currBlock.addOp(MethodOp(0, methodReg, type, args))
                    return RegistryArg(0, type)
                } else {
                    val reg = getNextRegistry()
                    currBlock.addOp(MethodOp(reg, methodReg, type, args))
                    currTypes[reg] = type
                    return RegistryArg(reg, type)
                }
            }
            is EClassVal -> {
                val obj = visitExpr(expr.expr_)
                val classReg = getNextRegistry()
                val classType = argToType(obj) as Class
                val valType = getClassVarType(classType.ident_, expr.ident_)
                val order = getFieldOrder(classType.ident_, expr.ident_)
                val valReg = getNextRegistry()

                currBlock.addOp(GetClassVarOp(classReg, classType.ident_, obj, order, valType))
                currBlock.addOp(LoadClassVarOp(valReg, valType, classReg))

                currTypes[valReg] = valType

                return RegistryArg(valReg, valType)
            }
            is ECast -> {
                val result = visitExpr(expr.expr_)
                val reg = getNextRegistry()
                currBlock.addOp(ClassCastOp(reg, expr.ident_, result))

                currTypes[reg] = Class(expr.ident_)

                return RegistryArg(reg, Class(expr.ident_))
            }
            else -> TODO("unknown expr")
        }
    }

    private fun getMethod(className: String, method: String): Triple<FuncDef, String, Int> {
        val c = definitions.classes[className]!!
        for (i in 0 until c.methodsList.size) {
            val t = c.methodsList[i]
            if (t.first == method) {
                val m = c.getMethodDef(method)
                return Triple(m, t.second, i)
            }
        }

        throw RuntimeException("not found method $method in class $className")
    }

    private fun getFieldOrder(className: String, fieldName: String): Int {
        val c = definitions.classes[className]!!
        val a = c.fieldsOrder[fieldName]
        return if (a == null) {
            getFieldOrder(c.parent.get(), fieldName)
        } else {
            a
        }
    }

    private fun visitOrAnd(expr_1: Expr, expr_2: Expr, isOr: Boolean): OpArgument {
        val left = visitExpr(expr_1)
        // This must be evaluated lazily.
        // For OR: if left was true, then don't evaluate right.
        // For AND otherwise
        val trueLabel = getNextLabel()
        val falseLabel = getNextLabel()
        val continueLabel = getNextLabel()

        currBlock.addOp(IfOp(left, trueLabel, falseLabel))

        val trueBlock = SSABlock(trueLabel, emptyList(), this)
        val falseBlock = SSABlock(falseLabel, emptyList(), this)
        currBlock.addNext(trueBlock)
        currBlock.addNext(falseBlock)

        currBlock = if (isOr) {
            trueBlock.addOp(JumpOp(continueLabel))
            falseBlock
        } else {
            falseBlock.addOp(JumpOp(continueLabel))
            trueBlock
        }

        val right = visitExpr(expr_2)
        val reg = getNextRegistry()
        if (isOr) {
            currBlock.addOp(OrOp(reg, left, right))
        } else {
            currBlock.addOp(AndOp(reg, left, right))
        }
        currBlock.addOp(JumpOp(continueLabel))

        var trueResult: OpArgument = BoolArg(true)
        var falseResult: OpArgument = RegistryArg(reg, Bool())
        var actualTrueLabel = trueLabel
        var actualFalseLabel = currBlock.label

        if (!isOr) {
            trueResult = falseResult
            falseResult = BoolArg(false)
            actualTrueLabel = currBlock.label
            actualFalseLabel = falseLabel
        }

        val phiReg = getNextRegistry()
        val phi = Phi("", phiReg, mapOf(actualTrueLabel to trueResult, actualFalseLabel to falseResult))

        val continueBlock = SSABlock(continueLabel, listOf(phi), this)
        currTypes[phiReg] = Bool()

        trueBlock.addNext(continueBlock)
        falseBlock.addNext(continueBlock)

        currBlock = continueBlock

        return RegistryArg(phiReg, Bool())
    }

    private fun visitListExpr(listexpr_: ListExpr, args: ListArg): List<OpArgument> {
        if (listexpr_.size != args.size) {
            throw RuntimeException("list of expressions size: ${listexpr_.size}, list of args size: ${args.size}")
        }

        return listexpr_.zip(args).map { pair ->
            var e = visitExpr(pair.first)
            val eType = argToType(e)
            if (eType is Class) {
                val argClassName = ((pair.second as Ar).type_ as Class).ident_
                if (eType.ident_ != argClassName) {
                    val newReg = getNextRegistry()
                    currBlock.addOp(ClassCastOp(newReg, argClassName, e))
                    e = RegistryArg(newReg, Class(argClassName))
                }
            }

            e
        }
    }
}
