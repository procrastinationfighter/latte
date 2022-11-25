package latte.topdeffetcher

import latte.Absyn.FnDef
import latte.Absyn.SubClassDef
import latte.Absyn.TopClassDef
import latte.common.*
import latte.latteParser
import latte.latteParserBaseVisitor
import java.util.*

class TopDefFetchingVisitor : latteParserBaseVisitor<LatteDefinitions>() {

    private var definitions: LatteDefinitions = LatteDefinitions()

    private fun printErr(message: String, line: Int, column: Int) {
        System.err.println("DEFINITION ERROR ($line,$column): $message")
    }

    override fun visitStart_Program(ctx: latteParser.Start_ProgramContext?): LatteDefinitions? {
        return if (ctx != null) {
            try {
                visitProgram(ctx.program())
                definitions
            } catch (e: LatteException) {
                printErr(e.message!!, e.line, e.column)
                null
            }
        } else {
            printErr("the program is empty", 0, 0)
            null
        }
    }

    override fun visitProgram(ctx: latteParser.ProgramContext?): LatteDefinitions {
        if (ctx == null) {
            throw LatteException("program is null", 0, 0)
        }

        visitListTopDef(ctx.listTopDef())

        // Check if a main function has been defined.
        val main = this.definitions.functions["main"] ?: throw LatteException("main function has not been defined", 0, 0)

        return this.definitions
    }

    override fun visitTopDef(ctx: latteParser.TopDefContext?): LatteDefinitions {
        val topDef = ctx?.result ?: throw LatteException("top def is null", 0, 0)

        if (topDef is FnDef) {
            val prev = this.definitions.functions.put(topDef.ident_, FuncDef(topDef.type_, topDef.listarg_))
            if (prev != null) {
                throw LatteException("redefinition of function ${topDef.ident_}", ctx.start!!.line, ctx.start!!.charPositionInLine)
            }
        } else if (topDef is TopClassDef) {
            val prev = this.definitions.classes.put(topDef.ident_, ClassDef(Optional.empty(), ctx.listClassDef()))
            if (prev != null) {
                throw LatteException("redefinition of class ${topDef.ident_}", ctx.start!!.line, ctx.start!!.charPositionInLine)
            }
        } else if (topDef is SubClassDef) {
            val prev = this.definitions.classes.put(topDef.ident_1, ClassDef(Optional.of(topDef.ident_2), ctx.listClassDef()))
            if (prev != null) {
                throw LatteException("redefinition of class ${topDef.ident_1}", ctx.start!!.line, ctx.start!!.charPositionInLine)
            }
        } else {
            throw LatteException("unknown definition type", ctx.start!!.line, ctx.start!!.charPositionInLine)
        }

        return this.definitions
    }

    override fun visitListTopDef(ctx: latteParser.ListTopDefContext?): LatteDefinitions {
        if (ctx == null) {
            return this.definitions
        }

        visitTopDef(ctx.topDef())
        return visitListTopDef(ctx.listTopDef())
    }
}