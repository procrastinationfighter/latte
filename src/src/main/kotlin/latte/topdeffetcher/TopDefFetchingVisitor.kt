package latte.topdeffetcher

import latte.common.*
import latte.latteParser
import latte.latteParserBaseVisitor

class TopDefFetchingVisitor : latteParserBaseVisitor<LatteDefinitions>() {

    fun printErr(message: String, line: Int, column: Int) {
        System.err.println("DEFINITION ERROR ($line,$column): $message")
    }

    override fun visitStart_Program(ctx: latteParser.Start_ProgramContext?): LatteDefinitions {
        if (ctx != null) {
            return visitProgram(ctx.program())
        } else {
            printErr("the program is empty", 0, 0)
            throw LatteException("the program is empty");
        }
    }

    override fun visitProgram(ctx: latteParser.ProgramContext?): LatteDefinitions {
        return super.visitProgram(ctx)
    }

    override fun visitTopDef(ctx: latteParser.TopDefContext?): LatteDefinitions {
        return super.visitTopDef(ctx)
    }

    override fun visitListTopDef(ctx: latteParser.ListTopDefContext?): LatteDefinitions {
        return super.visitListTopDef(ctx)
    }

    override fun visitArg(ctx: latteParser.ArgContext?): LatteDefinitions {
        return super.visitArg(ctx)
    }

    override fun visitListArg(ctx: latteParser.ListArgContext?): LatteDefinitions {
        return super.visitListArg(ctx)
    }

    override fun visitClassDef(ctx: latteParser.ClassDefContext?): LatteDefinitions {
        return super.visitClassDef(ctx)
    }

    override fun visitListClassDef(ctx: latteParser.ListClassDefContext?): LatteDefinitions {
        return super.visitListClassDef(ctx)
    }

    override fun visitType(ctx: latteParser.TypeContext?): LatteDefinitions {
        return super.visitType(ctx)
    }
}