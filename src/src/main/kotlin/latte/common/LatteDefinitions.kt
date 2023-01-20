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

class ClassDef(val parent: Optional<String>) {
    val variables: HashMap<String, Type> = HashMap()
    val methods: HashMap<String, FuncDef> = HashMap()
    val fieldsOrder: HashMap<String, Int> = HashMap()
    var j = 0
    var typesStr = ""
    var parentClass: ClassDef? = null

    fun calculateOrder(): Int {
        if (fieldsOrder.isNotEmpty()) {
            return j
        }
        j = parentClass?.calculateOrder() ?: 0
        val list = mutableListOf<Type>()
        for (p in variables) {
            fieldsOrder[p.key] = j
            list.add(p.value)
            j++
        }
        typesStr = list.joinToString(separator = ",") { typeToLlvm(it) }
        return j
    }

    fun setParent(parent: ClassDef) {
        this.parentClass = parent
    }

    constructor(parent: Optional<String>, defs: ListClassDefContext?) : this(parent) {
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