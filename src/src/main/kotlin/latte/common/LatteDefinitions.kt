package latte.common

import latte.Absyn.*
import latte.Absyn.Array
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

    fun getMethod(methodName: String): Triple<String, String, String> {
        for (t in methodsList) {
            if (t.first == methodName) {
                return t
            }
        }

        throw RuntimeException("method $methodName not found in class $name")
    }

    fun getMethodDef(methodName: String): FuncDef {
        val m = methods[methodName]
        return if (m != null) {
            m
        } else {
            parentClass!!.getMethodDef(methodName)
        }
    }

    fun setParent(parent: ClassDef) {
        this.parentClass = parent
    }

    fun isVirtualOk(method: String): Boolean {
        var c = this
        val thisM = methods[method]!!

        while (c.parent.isPresent) {
            c = c.parentClass!!
            val m = c.methods[method]
            if (m != null && !compareFunc(thisM, m)) {
                return false
            }
        }

        return true
    }

    private fun compareFunc(thisMethod: FuncDef, other: FuncDef): Boolean {
        if (!compareType(thisMethod.returnType, other.returnType)) {
            return false
        }
        if (thisMethod.args.size != other.args.size) {
            return false
        }

        thisMethod.args.zip(other.args).forEach {
            if (!compareType((it.first as Ar).type_, (it.second as Ar).type_)) {
                return false
            }
        }

        return true
    }

    private fun compareType(l: Type, r: Type): Boolean {
        return when (l) {
            is latte.Absyn.Int -> r is latte.Absyn.Int
            is Bool -> r is Bool
            is Str -> r is Str
            is Array -> r is Array && compareType(l.type_, r.type_)
            is Void -> r is Void
            is Class -> r is Class && (l.ident_ == r.ident_)
            is Null -> r is Null
            else -> false
        }
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