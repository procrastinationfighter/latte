package latte.llvmconverter

import latte.Absyn.*
import latte.common.typeToString
import latte.ssaconverter.getTypeDefaultValue
import latte.ssaconverter.ssa.*

fun typeToLlvm(type: Type): String {
    return when(type) {
        is latte.Absyn.Int -> "i32"
        is Str -> "i8*"
        is Bool -> "i1"
        is Void -> "void"
        is Class -> "%Class.${type.ident_}*"
        else -> throw RuntimeException("type not supported in typeToLlvm: ${typeToString(type)}")
    }
}

fun classNameToLlvm(name: String): String {
    return "%Class.$name"
}

class LLVMConverter(private val ssa: SSA) {

    private fun getExternalFuns(): String {
        return "declare void @printInt(i32)\n" +
                "declare void @printString(i8*)\n" +
                "declare void @error()\n" +
                "declare i32 @readInt()\n" +
                "declare i8* @readString()\n" +
                "declare i1 @compare.Str(i8*, i8*, i32)\n" +
                "declare i8* @add.Str(i8*, i8*)\n" +
                "declare i8* @alloc.Mem(i32)"
    }

    private fun strToLlvm(str: String): String {
        val type = "[${str.length + 1} x i8]"

        // We ignore special characters like \n
        return "$type c\"$str\\00\""
    }

    fun convert(): String {
        val strings = ssa.strings.map { "@${it.value} = internal constant ${strToLlvm(it.key)}" }.joinToString(separator="\n")
        val classes = ssa.classDefs.map { classToStr(it.key, it.value) }.joinToString(separator="\n")
        val classesInits = ssa.classDefs.map { getClassInit(it.key, it.value) }.joinToString(separator="\n")
        val classesVtables = getVtableTypes()
        val functions = ssa.defs.map { funToStr(it.value) }
        val external = getExternalFuns()
        val prefix = "$external\n\n$strings\n\n${classesVtables.first}\n\n$classes\n\n"
        return functions.joinToString(separator = "\n", prefix=prefix) + "\n\n${classesVtables.second}\n\n$classesInits"
    }

    private fun getVtableTypes(): Pair<String, String> {
        val x = ssa.classDefs.map { entry ->
            val p = entry.value.vtable.map {
                println("${it.second}.${it.first}")
                val f = ssa.defs["${it.second}.${it.first}"]!!
                val type = funcToLlvmType(f)
                Pair(type, "$type @${it.third}.${it.first}")
            }

            val vtableType = p.joinToString(separator = ", ", prefix = "%Vtable.${entry.key} = type {") { it.first } + "}"
            val vtableData = p.joinToString(separator=",\n", prefix="@Vtabledata.${entry.key} = global %Vtable.${entry.key} {\n") {it.second} + "\n}"

            Pair(vtableType, vtableData)
        }

        val types = x.joinToString(separator="\n") {it.first}
        val data = x.joinToString(separator="\n") {it.second}

        return Pair(types, data)
    }

    private fun funcToLlvmType(f: SSAFun): String {
        val args = f.args.joinToString(separator = ", ") { typeToLlvm((it as Ar).type_) }
        return "${typeToLlvm(f.type)}($args)*"
    }

    private fun classToStr(name: String, c: SSAClass): String {
        return "${classNameToLlvm(name)} = type { ${c.varsToLlvm()} }"
    }

    private fun funToStr(f: SSAFun): String {
        val header = getFunHeader(f)
        val body = getFunBody(f.block, f.type)

        return "$header\n$body\n}\n"
    }

    private fun getFunBody(block: SSABlock, funType: Type): String {
        val visited = mutableSetOf(block.label)
        val blocks = mutableListOf<String>()
        visitBlock(block, visited, blocks, funType)

        return blocks.joinToString(separator = "\n")
    }

    private fun visitBlock(block: SSABlock, visited: MutableSet<String>, blocks: MutableList<String>, funType: Type) {
        val ops = mutableListOf<String>()
        for (op in block.ops) {
            ops.add(op.toLlvm())
        }
        if (ops.isEmpty()) {
            if (funType is Void) {
                ops.add(ReturnVoidOp().toLlvm())
            } else {
                ops.add(UnreachableOp().toLlvm())
                blocks.add("${block.label}:\n  " + ops.joinToString(separator = "\n  "))
                return
            }
        } else if (!block.returned && !block.ended) {
            if (funType is Void) {
                ops.add(ReturnVoidOp().toLlvm())
            } else {
                throw RuntimeException("somehow found a block that does not end with return!")
            }
        }

        blocks.add("${block.label}:\n  " + ops.joinToString(separator = "\n  "))

        for (n in block.next) {
            if (!visited.contains(n.label)) {
                visited.add(n.label)
                visitBlock(n, visited, blocks, funType)
            }
        }
    }

    private fun getFunHeader(f: SSAFun): String {
        var i = 0
        val args = f.args.map { arToLlvm(it as Ar, i++) }.joinToString(separator = ", ")

        return "define ${typeToLlvm(f.type)} @${f.name} ($args) {"
    }

    private fun arToLlvm(arg: Ar, reg: Int): String {
        return "${typeToLlvm(arg.type_)} %reg$reg"
    }

    private fun getClassInit(name: String, c: SSAClass): String {
        val strings = mutableListOf<String>()
        val typeName = classNameToLlvm(name)
        strings.add("define void @InitClass.$name(${classNameToLlvm(name)}* %0) {")
        var i = 1;

        if (c.parentClass.isPresent) {
            val parentName = c.parentClass.get().name
            strings.add("%r$i = bitcast $typeName* %0 to ${classNameToLlvm(parentName)}*")
            strings.add("call void @InitClass.$parentName(${classNameToLlvm(parentName)}* %r$i)")
            i++
        }

        for (p in c.variables) {
            val t = typeToLlvm(p.value)
            strings.add("%r$i = getelementptr $typeName, $typeName* %0, i32 0, i32 ${c.order[p.key]!!}")
            strings.add("store $t ${getTypeDefaultValue(p.value).toLlvm()}, $t* %r$i")
            i++
        }

        return strings.joinToString(separator="\n  ") + "\n  ret void\n}\n"
    }

}