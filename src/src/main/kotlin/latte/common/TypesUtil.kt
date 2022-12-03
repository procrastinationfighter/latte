package latte.common

import latte.Absyn.*
import java.util.*

fun typeToString(type: Type?): String {
    if (type == null) {
        return "null"
    }
    return when (type) {
        is latte.Absyn.Int -> "int"
        is Bool -> "bool"
        is Str -> "string"
        is Void -> "void"
        is latte.Absyn.Array -> return typeToString(type.type_) + "[]"
        is Class -> type.ident_
        is Null -> "null"
        else -> "UNKNOWN TYPE"
    }
}