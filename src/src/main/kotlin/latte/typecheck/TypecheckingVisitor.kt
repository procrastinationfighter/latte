package latte.typecheck

import latte.Absyn.*
import latte.Absyn.Array
import latte.AllVisitor
import latte.common.FuncDef
import latte.common.LatteDefinitions
import latte.common.LatteException
import latte.latteParser
import latte.latteParser.BlockContext
import latte.latteParser.ListArgContext
import latte.latteParser.TypeContext
import latte.latteParserBaseVisitor
import org.antlr.v4.runtime.Token
import java.lang.Void
import java.util.*
import kotlin.collections.ArrayList

class TypecheckingVisitor(private val definitions: LatteDefinitions) : latteParserBaseVisitor<Type>() {

    private var currVariables: MutableList<MutableMap<String, Type>> = ArrayList()
    private var currFunctions: MutableList<MutableMap<String, FuncDef>> = ArrayList()

    // Local for functions
    private var currReturnType: Type? = null

    private fun typeExists(t: TypeContext): Boolean {
        val type = t.result
        if (type is latte.Absyn.Class && !definitions.classes.contains(type.ident_)) {
            throw LatteException("type ${type.ident_} has not been defined", t.start.line, t.start.charPositionInLine)
        }

        return true
    }

    private fun compareTypes(l: Type, r: Type): Boolean {
        when (l) {
            is latte.Absyn.Int -> return r is latte.Absyn.Int
            is latte.Absyn.Bool -> return r is latte.Absyn.Bool
            is latte.Absyn.Str -> return r is latte.Absyn.Str
            is latte.Absyn.Array -> return r is latte.Absyn.Array && compareTypes(l.type_, r.type_)
            is latte.Absyn.Void -> return r is latte.Absyn.Void
            is latte.Absyn.Class -> {
                return if (r is latte.Absyn.Class) {
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
        visitListArg(args)
        typeExists(returnType)
        currReturnType = returnType.result

        visitBlock(block)

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
        return super.visitArg(ctx)
    }

    override fun visitListArg(ctx: latteParser.ListArgContext?): Type {
        return super.visitListArg(ctx)
    }

    override fun visitClassDef(ctx: latteParser.ClassDefContext?): Type {
        return super.visitClassDef(ctx)
    }

    override fun visitListClassDef(ctx: latteParser.ListClassDefContext?): Type {
        return super.visitListClassDef(ctx)
    }

    override fun visitBlock(ctx: latteParser.BlockContext?): Type {
        return super.visitBlock(ctx)
    }

    override fun visitListStmt(ctx: latteParser.ListStmtContext?): Type {
        return super.visitListStmt(ctx)
    }

    override fun visitStmt(ctx: latteParser.StmtContext?): Type {
        return super.visitStmt(ctx)
    }

    override fun visitItem(ctx: latteParser.ItemContext?): Type {
        return super.visitItem(ctx)
    }

    override fun visitListItem(ctx: latteParser.ListItemContext?): Type {
        return super.visitListItem(ctx)
    }

    override fun visitType(ctx: latteParser.TypeContext?): Type {
        return super.visitType(ctx)
    }

    override fun visitListType(ctx: latteParser.ListTypeContext?): Type {
        return super.visitListType(ctx)
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