package latte.topdeffetcher

import latte.Absyn.*
import latte.common.*
import latte.common.ClassDef
import latte.latteParser
import latte.latteParserBaseVisitor
import java.util.*

class TopDefFetchingVisitor : latteParserBaseVisitor<LatteDefinitions>() {

    private var definitions: LatteDefinitions = LatteDefinitions()

    private fun printErr(message: String, line: Int, column: Int) {
        throw LatteException(message, line, column)
    }

    override fun visitStart_Program(ctx: latteParser.Start_ProgramContext?): LatteDefinitions? {
        return if (ctx != null) {
            try {
                addPredefinedFunctions()
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

    private fun getArgOfType(type: Type, name: String): Arg {
        return Ar(type, name)
    }

    private fun addPredefinedFunctions() {
        val printIntArgs = ListArg()
        printIntArgs.add(getArgOfType(Int(), "intToPrint"))
        definitions.functions["printInt"] = FuncDef(Void(), printIntArgs)
        val printStringArgs = ListArg()
        printStringArgs.add(getArgOfType(Str(), "stringToPrint"))
        definitions.functions["printString"] = FuncDef(Void(), printStringArgs)
        definitions.functions["error"] = FuncDef(Void(), ListArg())
        definitions.functions["readInt"] = FuncDef(Int(), ListArg())
        definitions.functions["readString"] = FuncDef(Str(), ListArg())
    }

    override fun visitProgram(ctx: latteParser.ProgramContext?): LatteDefinitions {
        if (ctx == null) {
            throw LatteException("program is null", 0, 0)
        }

        visitListTopDef(ctx.listTopDef())

        // Check if a main function has been defined.
        val main = this.definitions.functions["main"] ?: throw LatteException("main function has not been defined", 0, 0)
        if (!main.args.isEmpty()) {
            throw LatteException("main function can't take arguments", 0, 0)
        }

        for (c in definitions.classes) {
            if (c.value.parent.isPresent) {
                val parent = definitions.classes[c.value.parent.get()]
                    ?: throw LatteException("not found parent class ${c.value.parent.get()} for class ${c.key}", 0, 0)
                c.value.setParent(parent)
            }
        }
        for (c in definitions.classes.values) {
            c.calculateOrder()
        }

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