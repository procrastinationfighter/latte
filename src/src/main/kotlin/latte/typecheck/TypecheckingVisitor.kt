package latte.typecheck

import latte.Absyn.*
import latte.common.FuncDef
import latte.common.LatteDefinitions
import latte.common.LatteException
import latte.latteParser
import latte.latteParser.BlockContext
import latte.latteParser.ListArgContext
import latte.latteParser.TypeContext
import latte.latteParserBaseVisitor
import org.antlr.v4.runtime.Token
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.system.exitProcess

class TypecheckingVisitor(private val definitions: LatteDefinitions) : latteParserBaseVisitor<Type>() {

    private var currVariables: MutableList<MutableMap<String, Type>> = ArrayList()
    private var currFunctions: MutableList<MutableMap<String, FuncDef>> = ArrayList()

    // Local for functions
    private var currReturnType: Type? = null

    private fun getVariableType(name: Token): Type {
        for (map in currVariables.reversed()) {
            val type = map[name.text]
            if (type != null) {
                return type
            }
        }

        throw LatteException("variable ${name.text} not declared in this scope", name.line, name.charPositionInLine)
    }

    private fun addNewVariableScope() {
        currVariables.add(HashMap())
    }

    private fun removeLastVarScope() {
        currVariables.removeAt(currVariables.size - 1)
    }

    private fun addNewVariable(name: Token, type: Type) {
        val prev = currVariables[currVariables.size - 1].put(name.text, type)

        if (prev != null) {
            throw LatteException("variable $name is already declared in this scope", name.line, name.charPositionInLine)
        }
    }

    private fun getFunctionType(name: Token): FuncDef {
        for (map in currFunctions.reversed()) {
            val type = map[name.text]
            if (type != null) {
                return type
            }
        }

        throw LatteException("function ${name.text} not declared in this scope", name.line, name.charPositionInLine)
    }

    private fun unexpectedErrorExit(cond: Boolean, component: String) {
        if (cond) {
            System.err.println("unexpected error, something is null in $component")
        }
    }

    private fun typeExists(t: TypeContext): Boolean {
        val type = t.result
        if (type is latte.Absyn.Class && !definitions.classes.contains(type.ident_)) {
            throw LatteException("type ${type.ident_} has not been defined", t.start.line, t.start.charPositionInLine)
        } else if (type is latte.Absyn.Array) {
            return typeExists(t.type())
        }

        return true
    }

    // Checks if r is of type l
    private fun compareTypes(l: Type, r: Type): Boolean {
        when (l) {
            is latte.Absyn.Int -> return r is latte.Absyn.Int
            is latte.Absyn.Bool -> return r is latte.Absyn.Bool
            is latte.Absyn.Str -> return r is latte.Absyn.Str
            is latte.Absyn.Array -> return r is latte.Absyn.Array && compareTypes(l.type_, r.type_)
            is latte.Absyn.Void -> return r is latte.Absyn.Void
            is latte.Absyn.Class -> {
                return if (r is latte.Absyn.Class) {
                    // TODO: Implement for inheritance and nulls
                    l.ident_ == r.ident_ && definitions.classes.contains(l.ident_)
                } else {
                    false
                }
            }
        }

        System.err.println("Unexpected type: $l")
        return false
    }

    override fun visitStart_Program(ctx: latteParser.Start_ProgramContext?): Type? {
        return if (ctx != null) {
            visitProgram(ctx.program())
        } else {
            null
        }
    }

    override fun visitProgram(ctx: latteParser.ProgramContext?): Type {
        return visitListTopDef(ctx!!.listTopDef())
    }

    private fun visitFun(returnType: TypeContext, args: ListArgContext, block: BlockContext) {
        currVariables.add(HashMap())
        visitListArg(args)
        typeExists(returnType)
        currReturnType = returnType.result

        visitBlock(block)

        currVariables.removeAt(currVariables.size - 1)
        currReturnType = null
    }

    private fun prepareClass(def: latte.common.ClassDef) {
        if (def.parent.isPresent) {
            prepareClass(definitions.classes[def.parent.get()]!!)
        }
        currVariables.add(def.variables)
        currFunctions.add(def.methods)
    }

    private fun prepareClassVisit(name: Token, parent: Token?) {
        if (parent != null && !definitions.classes.contains(parent.text)) {
            throw LatteException("can't inherit from not defined class ${parent.text}", parent.line, parent.charPositionInLine)
        }
        val def = definitions.classes[name.text]!!
        prepareClass(def)
    }

    private fun leaveClassVisit() {
        // Get rid of all variables
        currVariables = ArrayList()
        // Get rid of all functions but the global ones
        val global = currFunctions[0]
        currFunctions = ArrayList()
        currFunctions.add(global)
    }

    override fun visitTopDef(ctx: latteParser.TopDefContext?): Type {
        return if (ctx!!.result is FnDef) {
            visitFun(ctx.type(), ctx.listArg(), ctx.block())

            Void()
        } else if (ctx.result is TopClassDef){ // top class def
            prepareClassVisit(ctx.p_2_2, null)
            visitListClassDef(ctx.listClassDef())
            leaveClassVisit()

            Void()
        } else { // sub class def
            prepareClassVisit(ctx.p_3_2, ctx.p_3_4)
            visitListClassDef(ctx.listClassDef())
            leaveClassVisit()

            Void()
        }
    }

    override fun visitListTopDef(ctx: latteParser.ListTopDefContext?): Type {
        if (ctx != null) {
            visitTopDef(ctx.topDef())
            visitListTopDef(ctx.listTopDef())
        }
        return Void()
    }

    override fun visitArg(ctx: latteParser.ArgContext?): Type {
        typeExists(ctx!!.type())
        val prev = currVariables[currVariables.size - 1].put(ctx.text, ctx.type().result)
        if (prev != null) {
            throw LatteException("argument with name ${ctx.text} already exists", ctx.start.line, ctx.start.charPositionInLine)
        }

        return ctx.type().result
    }

    override fun visitListArg(ctx: latteParser.ListArgContext?): Type {
        if (ctx != null) {
            visitArg(ctx.arg())
            visitListArg(ctx.listArg())
        }

        return Void()
    }

    override fun visitClassDef(ctx: latteParser.ClassDefContext?): Type {
        return if (ctx!!.result is ClassVarDef) {
            val prev = currVariables[currVariables.size - 1].put(ctx.p_1_2.text, ctx.type().result)
            if (prev != null) {
                throw LatteException("member variable with name ${ctx.p_1_2.text} already exists", ctx.start.line, ctx.start.charPositionInLine)
            }
            Void()
        } else { // topdef
            visitTopDef(ctx.topDef())
            Void()
        }
    }

    override fun visitListClassDef(ctx: latteParser.ListClassDefContext?): Type {
        if (ctx != null) {
            visitClassDef(ctx.classDef())
            visitListClassDef(ctx.listClassDef())
        }

        return Void()
    }

    override fun visitBlock(ctx: latteParser.BlockContext?): Type {
        currVariables.add(HashMap())
        visitListStmt(ctx!!.listStmt())

        return Void()
    }

    override fun visitListStmt(ctx: latteParser.ListStmtContext?): Type {
        if (ctx != null) {
            visitStmt(ctx.stmt())
            visitListStmt(ctx.listStmt())
        }

        return Void()
    }

    override fun visitStmt(ctx: latteParser.StmtContext?): Type {
        if (ctx != null) {

            when (ctx.result) {
                is latte.Absyn.Ass -> visitAss(ctx.IDENT(0).symbol, ctx.expr())
                is latte.Absyn.BStmt -> visitBStmt(ctx.block())
                is latte.Absyn.Cond -> visitCond(ctx.expr(), ctx.stmt(0))
                is latte.Absyn.CondElse -> visitCondElse(ctx.expr(), ctx.stmt(0), ctx.stmt(1))
                is latte.Absyn.Decl -> visitDecl(ctx.type(), ctx.listItem())
                is latte.Absyn.Decr -> visitDecr(ctx.IDENT(0).symbol)
                is latte.Absyn.Empty -> {}
                is latte.Absyn.Incr -> visitIncr(ctx.IDENT(0).symbol)
                is latte.Absyn.Ret -> visitRet(ctx.expr())
                is latte.Absyn.SExp -> visitExpr(ctx.expr())
                is latte.Absyn.VRet -> visitVRet(ctx.start)
                is latte.Absyn.While -> visitWhile(ctx.expr(), ctx.stmt(0))
                is latte.Absyn.For -> visitFor(ctx.type(), ctx.IDENT(0).symbol, ctx.IDENT(1).symbol, ctx.stmt(0))
            }
        }

        return Void();
    }

    private fun checkIfStmtNotDecl(stmt: latteParser.StmtContext, objectType: String) {
        if (stmt.result is latte.Absyn.Decl) {
            throw LatteException("a body of $objectType can't consist of a single declaration instruction", stmt.start.line, stmt.start.charPositionInLine)
        }
    }

    private fun visitFor(
        type: TypeContext?,
        variable: Token?,
        collection: Token?,
        stmt: latteParser.StmtContext?
    ) {
        unexpectedErrorExit(type == null || variable == null || collection == null || stmt == null, "for loop");
        val arrayType = getVariableType(collection!!)
        if (arrayType !is latte.Absyn.Array) {
            throw LatteException("for loop can be iterated only over arrays", collection.line, collection.charPositionInLine)
        }
        typeExists(type!!)
        if (!compareTypes(type.result, arrayType.type_)) {
            throw LatteException("variable type in for loop does not match the array type", type.start.line, type.start.charPositionInLine)
        }
        checkIfStmtNotDecl(stmt!!, "for loop")

        addNewVariableScope()
        addNewVariable(variable!!, type.result)

        visitStmt(stmt)

        removeLastVarScope()
    }

    private fun visitWhile(expr: latteParser.ExprContext?, stmt: latteParser.StmtContext?) {
        unexpectedErrorExit(expr == null || stmt == null, "while loop");

        val type = visitExpr(expr)
        if (!compareTypes(latte.Absyn.Bool(), type)) {
            throw LatteException("the condition of while loop must be of bool type", expr!!.start.line, expr.start.charPositionInLine)
        }

        checkIfStmtNotDecl(stmt!!, "while loop")
        visitStmt(stmt)
    }

    private fun visitVRet(t: Token) {
        unexpectedErrorExit(currReturnType == null, "void return")

        if (!compareTypes(currReturnType!!, latte.Absyn.Void())) {
            throw LatteException("an empty return statement in a non-void function", t.line, t.charPositionInLine)
        }
    }

    private fun visitRet(expr: latteParser.ExprContext?) {
        unexpectedErrorExit(currReturnType == null || expr == null, "return")

        val exprType = visitExpr(expr!!)
        if (!compareTypes(currReturnType!!, exprType)) {
            throw LatteException(
                "wrong return type, expected=$currReturnType, actual=$exprType",
                expr.start.line,
                expr.start.charPositionInLine
            )
        }
    }

    private fun visitIncr(symbol: Token?) {
        unexpectedErrorExit(symbol == null, "incr")
        val type = getVariableType(symbol!!)
        if (!compareTypes(latte.Absyn.Int(), type)) {
            throw LatteException("variable $symbol is not of type int", symbol.line, symbol.charPositionInLine)
        }
    }

    private fun visitDecr(symbol: Token?) {
        unexpectedErrorExit(symbol == null, "decr")
        val type = getVariableType(symbol!!)
        if (!compareTypes(latte.Absyn.Int(), type)) {
            throw LatteException("variable $symbol is not of type int", symbol.line, symbol.charPositionInLine)
        }
    }

    private fun visitDecl(type: latteParser.TypeContext?, listItem: latteParser.ListItemContext?) {
        unexpectedErrorExit(type == null || listItem == null, "decl")
        typeExists(type!!)

        visitListItem(type, listItem)
    }

    private fun visitCondElse(expr: latteParser.ExprContext?, stmt1: latteParser.StmtContext?, stmt2: latteParser.StmtContext?) {
        unexpectedErrorExit(expr == null || stmt1 == null || stmt2 == null, "if-else")

        val type = visitExpr(expr)
        if (!compareTypes(latte.Absyn.Bool(), type)) {
            throw LatteException("the condition of if-else must be of bool type", expr!!.start.line, expr.start.charPositionInLine)
        }

        checkIfStmtNotDecl(stmt1!!, "if-else")
        visitStmt(stmt1)
        checkIfStmtNotDecl(stmt2!!, "if-else")
        visitStmt(stmt2)
    }

    private fun visitCond(expr: latteParser.ExprContext?, stmt: latteParser.StmtContext?) {
        unexpectedErrorExit(expr == null || stmt == null, "if")

        val type = visitExpr(expr)
        if (!compareTypes(latte.Absyn.Bool(), type)) {
            throw LatteException("the condition of if must be of bool type", expr!!.start.line, expr.start.charPositionInLine)
        }

        checkIfStmtNotDecl(stmt!!, "if")
        visitStmt(stmt)
    }

    private fun visitBStmt(block: BlockContext?) {
        unexpectedErrorExit(block == null, "block")

        addNewVariableScope()
        visitListStmt(block!!.listStmt())
        removeLastVarScope()
    }

    private fun visitAss(ident: Token?, expr: latteParser.ExprContext?) {
        unexpectedErrorExit(ident == null || expr == null, "ass")

        val varType = getVariableType(ident!!)
        val exprType = visitExpr(expr!!)

        if (!compareTypes(varType, exprType)) {
            throw LatteException(
                "expression is not of required type, expected=$varType, actual=$exprType",
                expr.start.line,
                expr.start.charPositionInLine
            )
        }
    }

    private fun visitItem(type: latteParser.TypeContext?, ctx: latteParser.ItemContext?) {
        unexpectedErrorExit(type == null || ctx == null, "item")

        addNewVariable(ctx!!.IDENT().symbol, type!!.result)
        val exprType = visitExpr(ctx.expr())

        if (!compareTypes(type.result, exprType)) {
            throw LatteException(
                "expression is not of required type, expected=$type, actual=$exprType",
                ctx.expr().start.line,
                ctx.expr().start.charPositionInLine,
            )
        }
    }

    private fun visitListItem(type: latteParser.TypeContext?, ctx: latteParser.ListItemContext?) {
        if (ctx != null) {
            visitItem(type, ctx.item())
            visitListItem(type, ctx.listItem())
        }
    }

    override fun visitExpr6(ctx: latteParser.Expr6Context?): Type {
        return super.visitExpr6(ctx)
    }

    override fun visitChainExpr(ctx: latteParser.ChainExprContext?): Type {
        return super.visitChainExpr(ctx)
    }

    override fun visitChainVal(ctx: latteParser.ChainValContext?): Type {
        return super.visitChainVal(ctx)
    }

    override fun visitListChainExpr(ctx: latteParser.ListChainExprContext?): Type {
        return super.visitListChainExpr(ctx)
    }

    override fun visitExpr5(ctx: latteParser.Expr5Context?): Type {
        return super.visitExpr5(ctx)
    }

    override fun visitExpr4(ctx: latteParser.Expr4Context?): Type {
        return super.visitExpr4(ctx)
    }

    override fun visitExpr3(ctx: latteParser.Expr3Context?): Type {
        return super.visitExpr3(ctx)
    }

    override fun visitExpr2(ctx: latteParser.Expr2Context?): Type {
        return super.visitExpr2(ctx)
    }

    override fun visitExpr1(ctx: latteParser.Expr1Context?): Type {
        return super.visitExpr1(ctx)
    }

    override fun visitExpr(ctx: latteParser.ExprContext?): Type {
        return super.visitExpr(ctx)
    }

    override fun visitListExpr(ctx: latteParser.ListExprContext?): Type {
        return super.visitListExpr(ctx)
    }

    override fun visitAddOp(ctx: latteParser.AddOpContext?): Type {
        return super.visitAddOp(ctx)
    }

    override fun visitMulOp(ctx: latteParser.MulOpContext?): Type {
        return super.visitMulOp(ctx)
    }

    override fun visitRelOp(ctx: latteParser.RelOpContext?): Type {
        return super.visitRelOp(ctx)
    }

}