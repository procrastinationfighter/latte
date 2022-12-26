package latte.typecheck

import latte.Absyn.*
import latte.Absyn.Array
import latte.Absyn.Int
import latte.common.*
import latte.common.ClassDef
import latte.latteParser
import latte.latteParser.BlockContext
import latte.latteParser.ExprContext
import latte.latteParser.ListArgContext
import latte.latteParser.TypeContext
import latte.latteParserBaseVisitor
import org.antlr.v4.runtime.Token
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

fun unexpectedErrorExit(cond: Boolean, component: String) {
    if (cond) {
        throw RuntimeException("unexpected error, something is null in $component")
    }
}

class TypecheckingVisitor(private val definitions: LatteDefinitions) : latteParserBaseVisitor<Type>() {

    private var currVariables: MutableList<MutableMap<String, Type>> = ArrayList()
    private var currFunctions: MutableList<MutableMap<String, FuncDef>> = ArrayList()

    // Local for functions
    private var currReturnType: Type? = null

    // Local for classes
    private var currClass: String? = null

    private fun getClassName(type: Type): String {
        return when(type) {
            is Class -> type.ident_
            is latte.Absyn.Array -> "[]"
            else -> ""
        }
    }

    private fun getVariableType(name: Token): Type {
        for (map in currVariables.reversed()) {
            val type = map[name.text]
            if (type != null) {
                return type
            }
        }

        throw LatteException("variable ${name.text} not declared in this scope", name.line, name.charPositionInLine)
    }

    private fun getClassVariable(className: String, ident: Token?): Type {
        if (className == "[]") {
            if (ident!!.text == "length") {
                return Int()
            } else {
                throw LatteException("arrays contain only length member variable", ident.line, ident.charPositionInLine)
            }
        }
        var name = Optional.of(className)

        while (name.isPresent) {
            val currClass = definitions.classes[name.get()]
            if (currClass == null) {
                unexpectedErrorExit(true, "class tree var traversal")
            }
            name = currClass!!.parent
            val varType = currClass.variables[ident!!.text]
            if (varType != null) {
                return varType
            }
        }

        throw LatteException("variable ${ident!!.text} not found in class $className", ident.line, ident.charPositionInLine)
    }

    private fun getClassMethod(className: String, ident: Token?): FuncDef {
        var name = Optional.of(className)

        while (name.isPresent) {
            val currClass = definitions.classes[name.get()]
            if (currClass == null) {
                unexpectedErrorExit(true, "class tree method traversal")
            }
            name = currClass!!.parent
            val methodType = currClass.methods[ident!!.text]
            if (methodType != null) {
                return methodType
            }
        }

        throw LatteException("method ${ident!!.text} not found in class $className", ident.line, ident.charPositionInLine)
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
            throw LatteException("variable ${name.text} is already declared in this scope", name.line, name.charPositionInLine)
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

    private fun typeExists(t: TypeContext): Boolean {
        val type = t.result
        if (type is Class && !definitions.classes.contains(type.ident_)) {
            throw LatteException("type ${type.ident_} has not been defined", t.start.line, t.start.charPositionInLine)
        } else if (type is Array) {
            return typeExists(t.type())
        }

        return true
    }

    // Checks if r is of type l
    private fun compareTypes(l: Type, r: Type): Boolean {
        when (l) {
            is Int -> return r is Int
            is Bool -> return r is Bool
            is Str -> return r is Str
            is Array -> return r is Array && compareTypes(l.type_, r.type_)
            is Void -> return r is Void
            is Class -> {
                if (r is Class) {
                    var rId = Optional.of(r.ident_)
                    while (rId.isPresent) {
                        if (rId.get() == l.ident_) {
                            return true
                        }
                        val right = definitions.classes[rId.get()]

                        if (right != null) {
                            rId = right.parent
                        } else {
                            return false
                        }
                    }

                    return false
                } else return r is Null
            }
            is Null -> return r is Null
        }

        System.err.println("Unexpected type: $l")
        return false
    }

    override fun visitStart_Program(ctx: latteParser.Start_ProgramContext?): Type? {
        return if (ctx != null) {
            currFunctions.add(definitions.functions)
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

    private fun prepareClass(def: ClassDef) {
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
        currClass = name.text
    }

    private fun leaveClassVisit() {
        // Get rid of all variables
        currVariables = ArrayList()
        // Get rid of all functions but the global ones
        val global = currFunctions[0]
        currFunctions = ArrayList()
        currFunctions.add(global)
        currClass = null
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
        if (ctx.type().result is Void) {
            throw LatteException("void can't be an argument type", ctx.type().start.line, ctx.type().start.charPositionInLine)
        }
        val prev = currVariables[currVariables.size - 1].put(ctx.IDENT().text, ctx.type().result)
        if (prev != null) {
            throw LatteException("argument with name ${ctx.text} already exists", ctx.start.line, ctx.start.charPositionInLine)
        }

        return ctx.type().result
    }

    override fun visitListArg(ctx: ListArgContext?): Type {
        if (ctx?.arg() != null) {
            visitArg(ctx.arg())
            visitListArg(ctx.listArg())
        }

        return Void()
    }

    override fun visitClassDef(ctx: latteParser.ClassDefContext?): Type {
        return if (ctx!!.result is ClassVarDef) {
            typeExists(ctx.type())
            Void()
        } else { // topdef
            visitTopDef(ctx.topDef())
            Void()
        }
    }

    override fun visitListClassDef(ctx: latteParser.ListClassDefContext?): Type {
        if (ctx != null) {
            if (ctx.classDef() != null) {
                visitClassDef(ctx.classDef())
            }
            visitListClassDef(ctx.listClassDef())
        }

        return Void()
    }

    override fun visitBlock(ctx: BlockContext?): Type {
        currVariables.add(HashMap())
        visitListStmt(ctx!!.listStmt())

        return Void()
    }

    override fun visitListStmt(ctx: latteParser.ListStmtContext?): Type {
        if (ctx != null) {
            visitListStmt(ctx.listStmt())
            visitStmt(ctx.stmt())
        }

        return Void()
    }

    override fun visitStmt(ctx: latteParser.StmtContext?): Type {
        if (ctx != null) {
            when (ctx.result) {
                is ArrayAss -> visitArrayAss(ctx.expr6(), ctx.expr(0), ctx.expr(1))
                is Ass -> visitAss(ctx.IDENT(0).symbol, ctx.expr(0))
                is BStmt -> visitBStmt(ctx.block())
                is ClassAss -> visitClassAss(ctx.expr6(), ctx.IDENT(0).symbol, ctx.expr(0))
                is Cond -> visitCond(ctx.expr(0), ctx.stmt(0))
                is CondElse -> visitCondElse(ctx.expr(0), ctx.stmt(0), ctx.stmt(1))
                is Decl -> visitDecl(ctx.type(), ctx.listItem())
                is Decr -> visitDecr(ctx.IDENT(0).symbol)
                is Empty -> {}
                is Incr -> visitIncr(ctx.IDENT(0).symbol)
                is Ret -> visitRet(ctx.expr(0))
                is SExp -> visitExpr(ctx.expr(0))
                is VRet -> visitVRet(ctx.start)
                is While -> visitWhile(ctx.expr(0), ctx.stmt(0))
                is For -> visitFor(ctx.type(), ctx.IDENT(0).symbol, ctx.IDENT(1).symbol, ctx.stmt(0))
            }
        }

        return Void()
    }

    private fun checkIfStmtNotDecl(stmt: latteParser.StmtContext, objectType: String) {
        if (stmt.result is Decl) {
            throw LatteException("a body of $objectType can't consist of a single declaration instruction", stmt.start.line, stmt.start.charPositionInLine)
        }
    }

    private fun visitFor(
        type: TypeContext?,
        variable: Token?,
        collection: Token?,
        stmt: latteParser.StmtContext?
    ) {
        unexpectedErrorExit(type == null || variable == null || collection == null || stmt == null, "for loop")
        val arrayType = getVariableType(collection!!)
        if (arrayType !is Array) {
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

    private fun visitWhile(expr: ExprContext?, stmt: latteParser.StmtContext?) {
        unexpectedErrorExit(expr == null || stmt == null, "while loop")

        val type = visitExpr(expr)
        if (!compareTypes(Bool(), type)) {
            throw LatteException("the condition of while loop must be of bool type", expr!!.start.line, expr.start.charPositionInLine)
        }

        checkIfStmtNotDecl(stmt!!, "while loop")
        visitStmt(stmt)
    }

    private fun visitVRet(t: Token) {
        unexpectedErrorExit(currReturnType == null, "void return")

        if (!compareTypes(currReturnType!!, Void())) {
            throw LatteException("an empty return statement in a non-void function", t.line, t.charPositionInLine)
        }
    }

    private fun visitRet(expr: ExprContext?) {
        unexpectedErrorExit(currReturnType == null || expr == null, "return")

        val exprType = visitExpr(expr)

        if (!compareTypes(currReturnType!!, exprType)) {
            throw LatteException(
                "wrong return type, expected=${typeToString(currReturnType)}, actual=${typeToString(exprType)}",
                expr!!.start.line,
                expr.start.charPositionInLine
            )
        }
    }

    private fun visitIncr(symbol: Token?) {
        unexpectedErrorExit(symbol == null, "incr")
        val type = getVariableType(symbol!!)
        if (!compareTypes(Int(), type)) {
            throw LatteException("variable $symbol is not of type int", symbol.line, symbol.charPositionInLine)
        }
    }

    private fun visitDecr(symbol: Token?) {
        unexpectedErrorExit(symbol == null, "decr")
        val type = getVariableType(symbol!!)
        if (!compareTypes(Int(), type)) {
            throw LatteException("variable $symbol is not of type int", symbol.line, symbol.charPositionInLine)
        }
    }

    private fun visitDecl(type: TypeContext?, listItem: latteParser.ListItemContext?) {
        unexpectedErrorExit(type == null || listItem == null, "decl")
        typeExists(type!!)
        if (type.result is Void) {
            throw LatteException("void can't be a variable type", type.start.line, type.start.charPositionInLine)
        }

        visitListItem(type, listItem)
    }

    private fun visitCondElse(expr: ExprContext?, stmt1: latteParser.StmtContext?, stmt2: latteParser.StmtContext?) {
        unexpectedErrorExit(expr == null || stmt1 == null || stmt2 == null, "if-else")

        val type = visitExpr(expr)
        if (!compareTypes(Bool(), type)) {
            throw LatteException("the condition of if-else must be of bool type", expr!!.start.line, expr.start.charPositionInLine)
        }

        checkIfStmtNotDecl(stmt1!!, "if-else")
        visitStmt(stmt1)
        checkIfStmtNotDecl(stmt2!!, "if-else")
        visitStmt(stmt2)
    }

    private fun visitCond(expr: ExprContext?, stmt: latteParser.StmtContext?) {
        unexpectedErrorExit(expr == null || stmt == null, "if")

        val type = visitExpr(expr)
        if (!compareTypes(Bool(), type)) {
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

    private fun visitAss(left: Token?, expr: ExprContext?) {
        unexpectedErrorExit(left == null || expr == null, "ass")

        val varType = getVariableType(left!!)
        val exprType = visitExpr(expr!!)

        if (!compareTypes(varType, exprType)) {
            throw LatteException(
                "expression is not of required type, expected=${typeToString(varType)}, actual=${typeToString(exprType)}",
                expr.start.line,
                expr.start.charPositionInLine
            )
        }
    }

    private fun visitArrayAss(array: latteParser.Expr6Context?, index: ExprContext?, expr: ExprContext?): Type {
        unexpectedErrorExit(array == null || index == null || expr == null, "array ass")

        val left = visitArray(array!!, index!!)
        val right = visitExpr(expr)

        if (!compareTypes(left, right)) {
            throw LatteException(
                "expression is not of type, expected=${typeToString(left)}, actual=${typeToString(right)}",
                expr!!.start.line,
                expr.start.charPositionInLine
            )
        }

        return left
    }

    private fun visitClassAss(obj: latteParser.Expr6Context?, member: Token?, expr: ExprContext?): Type {
        unexpectedErrorExit(obj == null || member == null || expr == null, "class ass")

        val left = visitClassVal(obj!!, member!!)
        val right = visitExpr(expr!!)

        if (!compareTypes(left, right)) {
            throw LatteException(
                "expression is not of type, expected=${typeToString(left)}, actual=${typeToString(right)}",
                expr.start.line,
                expr.start.charPositionInLine
            )
        }

        return left
    }

    private fun visitItem(type: TypeContext?, ctx: latteParser.ItemContext?) {
        unexpectedErrorExit(type == null || ctx == null, "item")

        addNewVariable(ctx!!.IDENT().symbol, type!!.result)
        if (ctx.expr() == null) {
            // initalize later with zero value
            return
        }
        val exprType = visitExpr(ctx.expr())

        if (!compareTypes(type.result, exprType)) {
            throw LatteException(
                "expression is not of required type, expected=${typeToString(type.result)}, actual=${typeToString(exprType)}",
                ctx.expr().start.line,
                ctx.expr().start.charPositionInLine,
            )
        }
    }

    private fun visitListItem(type: TypeContext?, ctx: latteParser.ListItemContext?) {
        if (ctx != null) {
            visitItem(type, ctx.item())
            visitListItem(type, ctx.listItem())
        }
    }

    override fun visitExpr6(ctx: latteParser.Expr6Context?): Type {
        unexpectedErrorExit(ctx == null, "expr6")

        return when (ctx!!.result) {
            is EApp -> visitApp(ctx.IDENT().symbol, ctx.listExpr(), "")
            is EArray -> visitArray(ctx.expr6(), ctx.expr())
            is EClassCall -> visitClassCall(ctx.expr6(), ctx.IDENT().symbol, ctx.listExpr())
            is EClassVal -> visitClassVal(ctx.expr6(), ctx.IDENT().symbol)
            is ELitFalse -> Bool()
            is ELitTrue -> Bool()
            is ELitInt -> Int()
            is ENull -> Null()
            is EString -> Str()
            is ENewArr -> {
                // something is wrong, similar to binary ops
                if (ctx.type() != null) {
                    visitNewArr(ctx.type(), ctx.expr())
                } else {
                    visitExpr(ctx.expr())
                }
            }
            is ENewObj -> visitNewObj(ctx.type())
            is EVar -> visitVar(ctx.IDENT().symbol)
            else -> visitExpr(ctx.expr())
        }
    }

    private fun visitArray(expr: latteParser.Expr6Context, index: ExprContext): Type {
        val leftType = visitExpr6(expr)
        val rightType = visitExpr(index)

        if (leftType !is Array) {
            throw LatteException(
                "indexed value is not an array, it's ${typeToString(leftType)} instead",
                expr.start.line,
                expr.start.charPositionInLine,
            )
        }

        if (!compareTypes(Int(), rightType)) {
            throw LatteException("arrays can be indexed only by integers", index.start.line, index.start.charPositionInLine)
        }

        return leftType.type_
    }

    private fun visitClassCall(expr: latteParser.Expr6Context, methodName: Token, listExpr: latteParser.ListExprContext?): Type {
        val leftType = visitExpr6(expr)
        if (leftType !is Class) {
            throw LatteException(
                "method can be called only on objects, found type ${typeToString(leftType)} instead",
                expr.start.line,
                expr.start.charPositionInLine,
            )
        }

        return visitApp(methodName, listExpr, leftType.ident_)
    }

    private fun visitClassVal(expr: latteParser.Expr6Context, member: Token): Type {
        return when (val leftType = visitExpr6(expr)) {
            is Class -> getClassVariable(leftType.ident_, member)
            is Array -> getClassVariable("[]", member)
            else -> throw LatteException(
                "member variables can be extracted only from objects, found type ${typeToString(leftType)} instead",
                expr.start.line,
                expr.start.charPositionInLine,
            )
        }
    }

    private fun visitNewObj(type: TypeContext?): Type {
        unexpectedErrorExit(type == null, "new object")
        if (type!!.result !is Class) {
            throw LatteException(
                "only class and array instances can be initialized with new operator, found=${typeToString(type.result)}",
                type.start.line,
                type.start.charPositionInLine,
            )
        }

        if (definitions.classes[type.text] == null) {
            throw LatteException("class ${type.text} has not been defined", type.start.line, type.start.charPositionInLine)
        }

        return Class(type.text)
    }

    private fun visitNewArr(type: TypeContext?, expr: ExprContext?): Type {
        unexpectedErrorExit(type == null, "new array type")
        unexpectedErrorExit(expr == null, "new array expr")
        typeExists(type!!)
        if (type.result is Array) {
            throw LatteException("multidimensional arrays are not supported", type.start.line, type.start.charPositionInLine)
        }

        if (!compareTypes(Int(), visitExpr(expr))) {
            throw LatteException("array size must be an integer", expr!!.start.line, expr.start.charPositionInLine)
        }

        return Array(type.result)
    }

    private fun visitCast(type: Token?, expr: latteParser.Expr6Context?): Type {
        unexpectedErrorExit(type == null || expr == null, "cast")
        val exprType = visitExpr6(expr)
        val t = Class(type!!.text)

        if (!compareTypes(t, exprType)) {
            throw LatteException("expression is not of type ${typeToString(t)}", expr!!.start.line, expr.start.charPositionInLine)
        }

        return t
    }


    private fun visitApp(ident: Token?, listExpr: latteParser.ListExprContext?, className: String): Type {
        unexpectedErrorExit(ident == null, "app")

        val func: FuncDef = if (className == "") {
            getFunctionType(ident!!)
        } else {
            getClassMethod(className, ident!!)
        }

        checkFunctionCall(listExpr, func, ident)

        return func.returnType
    }

    private fun checkFunctionCall(listExpr: latteParser.ListExprContext?, func: FuncDef, ident: Token) {
        if (listExpr?.expr() == null && func.args.size == 0) {
            return
        }

        var exprs = listExpr
        var currArg = 0

        while (exprs?.expr() != null && currArg < func.args.size) {
            val currExpr = exprs.expr()
            val exprType = visitExpr(currExpr)
            val arg = func.args[currArg] as Ar
            if (!compareTypes(arg.type_, exprType)) {
                throw LatteException(
                    "argument ${arg.ident_} of function ${ident.text} received value of incorrect type, " +
                            "expected type=${typeToString(arg.type_)}, " +
                            "actual type=${typeToString(exprType)}",
                    currExpr.start.line,
                    currExpr.start.charPositionInLine,
                )
            }

            exprs = exprs.listExpr()
            currArg++
        }

        if (exprs?.expr() != null) {
            throw LatteException(
                "function ${ident.text} received too many arguments",
                listExpr!!.start.line,
                listExpr.start.charPositionInLine,
            )
        } else if (currArg < func.args.size) {
            throw LatteException(
                "function ${ident.text} received too few arguments",
                listExpr!!.start.line,
                listExpr.start.charPositionInLine,
            )
        }
    }

    private fun visitVar(ident: Token?): Type {
        unexpectedErrorExit(ident == null, "var")

        return if (ident!!.text == "self") {
            return Class(currClass)
        } else {
            getVariableType(ident)
        }
    }

    override fun visitExpr5(ctx: latteParser.Expr5Context?): Type {
        unexpectedErrorExit(ctx == null, "expr5")

        return when(ctx!!.result) {
            is ECast -> visitCast(ctx.IDENT().symbol, ctx.expr6())
            is Neg -> visitNeg(ctx.expr6())
            is Not -> visitNot(ctx.expr6())
            else -> visitExpr6(ctx.expr6())
        }
    }

    private fun visitNot(expr: latteParser.Expr6Context?): Type {
        unexpectedErrorExit(expr == null, "not")

        val type = visitExpr6(expr)
        if (!compareTypes(Bool(), type)) {
            throw LatteException("not can be used only on boolean values", expr!!.start.line, expr.start.charPositionInLine)
        }

        return Bool()
    }

    private fun visitNeg(expr: latteParser.Expr6Context?): Type {
        unexpectedErrorExit(expr == null, "neg")

        val type = visitExpr6(expr)
        if (!compareTypes(Int(), type)) {
            throw LatteException("only integer values can be negated", expr!!.start.line, expr.start.charPositionInLine)
        }

        return Int()
    }

    override fun visitExpr4(ctx: latteParser.Expr4Context?): Type {
        unexpectedErrorExit(ctx == null, "expr4")

        return when(ctx!!.result) {
            is EMul -> {
                // Something is wrong, but this works.
                if (ctx.expr4() != null) {
                    visitMulOp(ctx.expr4(), ctx.expr5())
                } else {
                    visitExpr5(ctx.expr5())
                }
            }
            else -> visitExpr5(ctx.expr5())
        }
    }

    override fun visitExpr3(ctx: latteParser.Expr3Context?): Type {
        unexpectedErrorExit(ctx == null, "expr3")

        return when(ctx!!.result) {
            is EAdd -> {
                // Something is wrong, but this works.
                if (ctx.expr3() != null) {
                    visitAddOp(ctx.addOp(), ctx.expr3(), ctx.expr4())
                } else {
                    visitExpr4(ctx.expr4())
                }
            }
            else -> visitExpr4(ctx.expr4())
        }
    }

    override fun visitExpr2(ctx: latteParser.Expr2Context?): Type {
        unexpectedErrorExit(ctx == null, "expr2")

        return when(ctx!!.result) {
            is ERel -> {
                // Something is wrong, but this works.
                if (ctx.expr2() != null) {
                    visitRelOp(ctx.relOp(), ctx.expr2(), ctx.expr3())
                } else {
                    visitExpr3(ctx.expr3())
                }
            }
            else -> visitExpr3(ctx.expr3())
        }
    }

    override fun visitExpr1(ctx: latteParser.Expr1Context?): Type {
        unexpectedErrorExit(ctx == null, "expr1")

        return when(ctx!!.result) {
            is EAnd -> {
                // Something is wrong, but this works.
                if (ctx.expr1() != null) {
                    visitAnd(ctx.expr2(), ctx.expr1())
                } else {
                    visitExpr2(ctx.expr2())
                }
            }
            else -> visitExpr2(ctx.expr2())
        }
    }

    private fun visitAnd(left: latteParser.Expr2Context?, right: latteParser.Expr1Context?): Type {
        unexpectedErrorExit(left == null, "and left")
        unexpectedErrorExit(right == null, "and right")

        val leftType = visitExpr2(left)
        if (!compareTypes(Bool(), leftType)) {
            throw LatteException("and operation can be done only on boolean values", left!!.start.line, left.start.charPositionInLine)
        }

        val rightType = visitExpr1(right)
        if (!compareTypes(Bool(), rightType)) {
            throw LatteException("and operation can be done only on boolean values", right!!.start.line, right.start.charPositionInLine)
        }

        return Bool()
    }

    override fun visitExpr(ctx: ExprContext?): Type {
        unexpectedErrorExit(ctx == null, "expr")

        return when(ctx!!.result) {
            is EOr -> {
                // Something is wrong, but this works.
                if (ctx.expr() != null) {
                    visitOr(ctx.expr1(), ctx.expr())
                } else {
                    visitExpr1(ctx.expr1())
                }
            }
            else -> visitExpr1(ctx.expr1())
        }
    }

    private fun visitOr(left: latteParser.Expr1Context?, right: ExprContext?): Type {
        unexpectedErrorExit(left == null || right == null, "or")

        val leftType = visitExpr1(left)
        if (!compareTypes(Bool(), leftType)) {
            throw LatteException("or operation can be done only on boolean values", left!!.start.line, left.start.charPositionInLine)
        }

        val rightType = visitExpr(right)
        if (!compareTypes(Bool(), rightType)) {
            throw LatteException("or operation can be done only on boolean values", right!!.start.line, right.start.charPositionInLine)
        }

        return Bool()
    }

    private fun visitAddOp(
        ctx: latteParser.AddOpContext,
        left: latteParser.Expr3Context,
        right: latteParser.Expr4Context,
    ): Type {
        val leftType = visitExpr3(left)
        val isInt = compareTypes(Int(), leftType)
        val isString = compareTypes(Str(), leftType)

        if (ctx.result is Plus && !isInt && !isString) {
            throw LatteException(
                "addition can be used only on integer and string values, found value of type ${typeToString(leftType)}",
                left.start.line,
                left.start.charPositionInLine,
            )
        } else if (ctx.result is Minus && !isInt) {
            throw LatteException(
                "subtraction can be used only on integer values, found value of type ${typeToString(leftType)}",
                left.start.line,
                left.start.charPositionInLine,
            )
        }

        val rightType = visitExpr4(right)
        if (!compareTypes(leftType, rightType)) {
            throw LatteException(
                "addition and subtraction can be used only on values of the same type, " +
                        "left=${typeToString(leftType)}, " +
                        "right=${typeToString(rightType)}",
                right.start.line,
                right.start.charPositionInLine,
            )
        }

        return leftType
    }

    private fun visitMulOp(
        left: latteParser.Expr4Context?,
        right: latteParser.Expr5Context?,
    ): Type {
        unexpectedErrorExit(left == null || right == null, "mul")
        val leftType = visitExpr4(left!!)
        if (!compareTypes(Int(), leftType)) {
            throw LatteException(
                "multiplication, division and modulo can be used only on integer values, found value of type ${typeToString(leftType)}",
                left.start.line,
                left.start.charPositionInLine,
            )
        }

        val rightType = visitExpr5(right!!)
        if (!compareTypes(Int(), rightType)) {
            throw LatteException(
                "multiplication, division and modulo can be used only on integer values, found value of type ${typeToString(rightType)}",
                right.start.line,
                right.start.charPositionInLine,
            )
        }
        return Int()
    }

    private fun visitRelOp(
        ctx: latteParser.RelOpContext,
        left: latteParser.Expr2Context,
        right: latteParser.Expr3Context,
    ): Type {
        val leftType = visitExpr2(left)
        // FIXME: decide later if only arrays can't be compared
        if (leftType is Array) {
            throw LatteException(
                "comparisons cannot be done on arrays",
                left.start.line,
                left.start.charPositionInLine,
            )
        } else if (leftType is Void) {
            throw LatteException(
                "comparisons cannot be done on void",
                left.start.line,
                left.start.charPositionInLine,
            )
        } else if (ctx.result !is EQU && leftType is Class) {
            throw LatteException(
                "objects can only be compared as equal or not (==)",
                left.start.line,
                left.start.charPositionInLine,
            )
        }

        val rightType = visitExpr3(right)

        // compare both combinations, because of comparing subclasses
        if (!compareTypes(leftType, rightType) && !compareTypes(rightType, leftType)) {
            throw LatteException(
                "cannot compare type ${typeToString(leftType)} with type ${typeToString(rightType)}",
                right.start.line,
                right.start.charPositionInLine,
            )
        }
        return Bool()
    }

}