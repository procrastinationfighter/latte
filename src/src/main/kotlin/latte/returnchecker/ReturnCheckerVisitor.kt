package latte.returnchecker

import latte.Absyn.*
import latte.common.LatteException
import latte.latteParser
import latte.typecheck.unexpectedErrorExit
import org.antlr.v4.runtime.Token
import java.util.*

class ReturnCheckerVisitor {
    fun visitStartProgram(ctx: latteParser.Start_ProgramContext?) {
        unexpectedErrorExit(ctx == null, "start program")
        visitProgram(ctx!!.program())
    }

    private fun visitProgram(ctx: latteParser.ProgramContext?) {
        unexpectedErrorExit(ctx == null, "program")
        visitListTopDef(ctx!!.listTopDef())
    }

    private fun visitTopDef(ctx: latteParser.TopDefContext?) {
        if (ctx == null) {
            return
        }

        if ((ctx.result is FnDef) && (ctx.type().result !is Void)) {
            visitFun(ctx.IDENT(0).symbol, ctx.block())
        } else if (ctx.result is TopClassDef || ctx.result is SubClassDef) {
            visitListClassDef(ctx.listClassDef())
        }
    }

    private fun visitListTopDef(ctx: latteParser.ListTopDefContext?) {
        unexpectedErrorExit(ctx == null, "list top def")
        var listTopDef = ctx

        while (listTopDef != null) {
            visitTopDef(listTopDef.topDef())
            listTopDef = listTopDef.listTopDef()
        }
    }

    private fun visitClassDef(ctx: latteParser.ClassDefContext?) {
        unexpectedErrorExit(ctx == null, "class def")
        if (ctx!!.result is ClassTopDef) {
            if ((ctx.topDef()!!.result is FnDef) && (ctx.topDef().type().result !is Void)) {
                visitFun(ctx.topDef().IDENT(0).symbol, ctx.topDef().block())
            }
        }
    }

    private fun visitListClassDef(ctx: latteParser.ListClassDefContext?) {
        unexpectedErrorExit(ctx == null, "list class def")
        var listClassDef = ctx!!.listClassDef()

        while (listClassDef?.classDef() != null) {
            visitClassDef(listClassDef.classDef())
            listClassDef = listClassDef.listClassDef()
        }
    }

    private fun visitFun(name: Token, ctx: latteParser.BlockContext?) {
        unexpectedErrorExit(ctx == null, "fun block")
        // Visit all statements with BFS
        if (!visitListStmt(ctx!!.listStmt())) {
            throw LatteException("not all branches in ${name.text} end with return", name.line, name.charPositionInLine)
        }
    }

    private fun visitListStmt(ctx: latteParser.ListStmtContext?): Boolean {
        unexpectedErrorExit(ctx == null, "list stmt")

        var listStmt = ctx

        while (listStmt != null) {
            if (listStmt.stmt() != null && visitStmt(listStmt.stmt())) {
                return true
            }

            listStmt = listStmt.listStmt()
        }

        return false
    }

    private fun visitStmt(ctx: latteParser.StmtContext?): Boolean {
        return when (ctx!!.result) {
            is BStmt -> visitListStmt(ctx.block().listStmt())
            is Cond -> {
                val cond = getExprConstBool(ctx.expr(0))
                if (cond.isPresent && cond.get()) {
                    // If we always enter the if, check if it has returns
                    visitStmt(ctx.stmt(0))
                } else {
                    false
                }
            }
            is CondElse -> {
                val cond = getExprConstBool(ctx.expr(0))
                if (!cond.isPresent) {
                    // return true only if both branches are true
                    visitStmt(ctx.stmt(0)) && visitStmt(ctx.stmt(1))
                } else {
                    if (cond.get()) {
                        visitStmt(ctx.stmt(0))
                    } else {
                        visitStmt(ctx.stmt(1))
                    }
                }
            }
            is Ret -> true
            is VRet -> true
            is While -> {
                val cond = getExprConstBool(ctx.expr(0))
                if (!cond.isPresent) {
                    visitStmt(ctx.stmt(0))
                } else {
                    // Treat while (true) as positive (because there are no break statements)
                    cond.get()
                }
            }
            else -> false
        }
    }

    private fun getExprConstBool(ctx: latteParser.ExprContext?): Optional<Boolean> {
        unexpectedErrorExit(ctx == null, "expr")

        return if (ctx!!.result is ELitTrue) {
            Optional.of(true)
        } else if (ctx.result is ELitFalse) {
            Optional.of(false)
        } else {
            Optional.empty()
        }
    }
}