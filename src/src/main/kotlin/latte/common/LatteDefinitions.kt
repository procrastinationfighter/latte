package latte.common

import latte.Absyn.*
import latte.latteParser.ListClassDefContext
import latte.llvmconverter.typeToLlvm
import java.util.*
import kotlin.collections.HashMap

class LatteDefinitions {
    var functions: MutableMap<String, FuncDef> = HashMap()
    var classes: MutableMap<String, ClassDef> = HashMap()
}

class FuncDef(val returnType: Type, val args: ListArg)

class ClassDef(val name: String, val parent: Optional<String>) {
    val variables: HashMap<String, Type> = HashMap()
    val methods: HashMap<String, FuncDef> = HashMap()
    val fieldsOrder: HashMap<String, Int> = HashMap()
    var j = 1
    var methodsList: List<Triple<String, String, String>> = listOf()
    var typesStr = ""
    var parentClass: ClassDef? = null

    fun calculateOrder(): Pair<Int, List<Triple<String, String, String>>> {
        if (fieldsOrder.isNotEmpty()) {
            return Pair(j, methodsList)
        }
        val par = parentClass?.calculateOrder() ?: Pair(1, listOf())
        j = par.first
        val mets: MutableList<Triple<String, String, String>> = par.second.toMutableList()//mutableListOf(par.second)
        val list = mutableListOf<Type>()
        for (p in variables) {
            fieldsOrder[p.key] = j
            list.add(p.value)
            j++
        }

        outer@ for (f in methods) {
            for (i in 0 until mets.size) {
                val a = mets[i]
                if (a.first == f.key) {
                    mets[i] = Triple(a.first, a.second, name)
                    continue@outer
                }
            }
            mets.add(Triple(f.key, name, name))
        }
        typesStr = list.joinToString(separator = ",") { typeToLlvm(it) }
        methodsList = mets.toList()
        return Pair(j, methodsList)
    }

    fun setParent(parent: ClassDef) {
        this.parentClass = parent
    }

    constructor(name: String, parent: Optional<String>, defs: ListClassDefContext?) : this(name, parent) {
        var classDefs = defs
        while (classDefs?.classDef() != null) {
            val classDef = classDefs.classDef()
            val def = classDef.result
            if (def is ClassTopDef) {
                if (def.topdef_ is FnDef) {
                    val fnDef = def.topdef_

                    if (methods.put(fnDef.ident_, FuncDef(fnDef.type_, fnDef.listarg_))!= null) {
                        throw LatteException("redefinition of class method", classDef.start.line, classDef.start.charPositionInLine)
                    }
                } else { // nested class def, throw an exception
                    throw LatteException("nested class definitions are not supported", classDef.start.line, classDef.start.charPositionInLine)
                }
            } else { // member variable
                val varDef = def as ClassVarDef

                if (variables.put(varDef.ident_, varDef.type_) != null) {
                    throw LatteException("redefinition of class member variable", classDef.start.line, classDef.start.charPositionInLine)
                }
            }

            classDefs = classDefs.listClassDef()
        }
    }
}