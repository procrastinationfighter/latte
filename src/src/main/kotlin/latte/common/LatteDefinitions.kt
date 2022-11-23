package latte.common

import latte.Absyn.*
import java.util.*
import kotlin.collections.HashMap

class LatteDefinitions {
    var functions: Map<String, FuncDef> = HashMap()
    var classes: Map<String, ClassDef> = HashMap()
}

class FuncDef(val returnType: Type, val args: ListArg)

class ClassDef(val parent: Optional<String>, defs: ListClassDef) {
    val variables: HashMap<String, Type> = HashMap()
    val methods: HashMap<String, FuncDef> = HashMap()

    init {
        for (def in defs) {
            if (def is ClassTopDef) {
                if (def.topdef_ is FnDef) {
                    val fnDef = def.topdef_
                    methods[fnDef.ident_] = FuncDef(fnDef.type_, fnDef.listarg_)
                } else { // nested class def, throw an exception
                    throw FeatureNotSupported("Nested class definitions are not supported")
                }
            } else { // member variable
                val varDef = def as ClassVarDef
                variables[varDef.ident_] = varDef.type_
            }
        }
    }
}