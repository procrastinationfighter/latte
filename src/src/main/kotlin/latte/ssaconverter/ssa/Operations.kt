package latte.ssaconverter.ssa

import latte.Absyn.*
import latte.Absyn.AddOp
import latte.common.typeToString
import latte.llvmconverter.classNameToLlvm
import latte.llvmconverter.typeToLlvm
import latte.ssaconverter.argToType
import kotlin.system.exitProcess

interface OpArgument {
    fun print(): String
    fun toLlvm(): String
    fun toLlvmType(): String

    fun argEquals(o: OpArgument): Boolean
}

class RegistryArg(val number: Int, val type: Type): OpArgument {
    override fun print(): String {
        return "%$number"
    }

    override fun toLlvm(): String {
        return "%reg${number}"
    }

    override fun toLlvmType(): String {
        return typeToLlvm(type)
    }

    override fun argEquals(o: OpArgument): Boolean {
        return o is RegistryArg && number == o.number
    }
}

class IntArg(val number: Int): OpArgument {
    override fun print(): String {
        return "$number"
    }

    override fun toLlvm(): String {
        return number.toString()
    }

    override fun toLlvmType(): String {
        return "i32"
    }

    override fun argEquals(o: OpArgument): Boolean {
        return o is IntArg && number == o.number
    }
}

class BoolArg(val b: Boolean): OpArgument {
    override fun print(): String {
        return "$b"
    }

    override fun toLlvm(): String {
        return if (b) {
            "1"
        } else {
            "0"
        }
    }

    override fun toLlvmType(): String {
        return "i1"
    }

    override fun argEquals(o: OpArgument): Boolean {
        return o is BoolArg && b == o.b
    }
}

// s is the name of the variable that contains the string
class StringArg(val s: String, val len: Int): OpArgument {
    override fun print(): String {
        return "\"$s\""
    }

    override fun toLlvm(): String {
        return "getelementptr ([$len x i8], [$len x i8]* @$s, i64 0, i64 0)"
    }

    override fun toLlvmType(): String {
        return "i8*"
    }

    override fun argEquals(o: OpArgument): Boolean {
        return o is StringArg && s == o.s
    }


}

abstract class Op {
    abstract fun print()
    abstract fun toLlvm(): String
    abstract fun reduce(replaceMap: MutableMap<Int, OpArgument>)
    abstract fun opEquals(otherOp: Op): Boolean
    abstract fun getReplacement(): OpArgument

    abstract fun updateUsed(s: MutableSet<Int>)
    abstract fun updateAssigned(s: MutableSet<Int>)
}
abstract class RegistryOp(val result: RegistryArg): Op() {
    override fun print() {
        println("%${result.number} = ${printOp()}")
    }

    abstract fun printOp(): String

    override fun toLlvm(): String {
        return if (this is AppOp && this.type is latte.Absyn.Void) {
            opToLlvm()
        } else {
            "%reg${result.number} = ${opToLlvm()}"
        }
    }

    abstract fun opToLlvm(): String

    override fun getReplacement(): OpArgument {
        return result
    }

    override fun updateAssigned(s: MutableSet<Int>) {
        s.add(result.number)
    }
}

class GetClassVarOp(result: Int, val className: String, var classLoc: OpArgument, val varName: Int, varType: Type) : RegistryOp(
    RegistryArg(result, varType)
) {
    override fun printOp(): String {
        return "&$className.$varName"
    }

    override fun opToLlvm(): String {
        val c = classNameToLlvm(className)
        return "getelementptr $c, $c* ${classLoc.toLlvm()}, i32 0, i32 $varName"
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {
        if (classLoc is RegistryArg) {
            val r = replaceMap[(classLoc as RegistryArg).number]
            if (r != null) {
                classLoc = r
            }
        } else {
            throw RuntimeException("expected object to be in a registry, found $classLoc instead")
        }
    }

    override fun opEquals(otherOp: Op): Boolean {
        // Two getClassVar are equal if refer to the same object and the same variable
        return otherOp is GetClassVarOp && otherOp.classLoc.argEquals(classLoc) && otherOp.varName == varName
    }

    override fun updateUsed(s: MutableSet<Int>) {
        if (classLoc is RegistryArg) {
            s.add((classLoc as RegistryArg).number)
        }
    }

}


class PhiOp(val phi: Phi): RegistryOp(RegistryArg(phi.registry, phi.getType())) {
    override fun printOp(): String {
        return phi.values.map { "${it.key}: ${it.value.print()}" }.joinToString(prefix = "phi[", separator = ", ", postfix = "]")
    }

    override fun opToLlvm(): String {
        val branches = phi.values.map { "[${it.value.toLlvm()}, %${it.key}]" }.joinToString(separator = ", ")
        return "phi ${typeToLlvm(phi.getType())} $branches"
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {
        phi.values = phi.values.mapValues {
            val v = it.value
            val newV = if (v is RegistryOp && replaceMap[v.result.number] != null) {
                replaceMap[v.result.number]!!
            } else {
                v
            }

            newV
        }
    }

    override fun opEquals(otherOp: Op): Boolean {
        return if (otherOp is PhiOp) {
            phi.values == otherOp.phi.values
        } else {
            false
        }
    }

    override fun updateUsed(s: MutableSet<Int>) {
        for (pair in phi.values) {
            if (pair.value is RegistryArg) {
                s.add((pair.value as RegistryArg).number)
            }
        }
    }
}

class AppOp(result: Int, val name: String, val type: Type, var args: List<OpArgument>): RegistryOp(RegistryArg(result, type)) {
    override fun printOp(): String {
        val argsStr = args.joinToString(separator = ", ") { it.print() }
        return "call $name ($argsStr): ${typeToString(type)}"
    }

    override fun opToLlvm(): String {
        val argsStr = args.joinToString(separator = ", ") { "${typeToLlvm(argToType(it))} ${it.toLlvm()}" }
        return "call ${typeToLlvm(type)} @$name($argsStr)"
    }

    override fun opEquals(otherOp: Op): Boolean {
        // Function calls are never equal to each other because of in-out operations.
        return false
    }

    override fun updateUsed(s: MutableSet<Int>) {
        for (arg in args) {
            if (arg is RegistryArg) {
                s.add(arg.number)
            }
        }
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {
        args = args.map {
            if (it is RegistryOp) {
                replaceMap[it.result.number] ?: it
            } else {
                it
            }
        }
    }
}

abstract class UnaryOp(result: Int, var arg: OpArgument, type: Type): RegistryOp(RegistryArg(result, type)) {
    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {
        if (arg is RegistryArg) {
            val x = replaceMap[(arg as RegistryArg).number]
            if (x != null) {
                arg = x
            }
        }
    }

    override fun opEquals(otherOp: Op): Boolean {
        val otherArg = if (otherOp is UnaryOp) otherOp.arg else null

        return otherArg != null
                && this::class == otherOp::class
                && arg.argEquals(otherArg)
    }

    override fun updateUsed(s: MutableSet<Int>) {
        if (arg is RegistryArg) {
            s.add((arg as RegistryArg).number)
        }
    }
}

class NotOp(result: Int, arg: OpArgument): UnaryOp(result, arg, Bool()) {
    override fun printOp(): String {
        return "not ${arg.print()}"
    }

    override fun opToLlvm(): String {
        return "sub i1 1, ${arg.toLlvm()}"
    }
}
class NegOp(result: Int, arg: OpArgument): UnaryOp(result, arg, latte.Absyn.Int()) {
    override fun printOp(): String {
        return "neg ${arg.print()}"
    }

    override fun opToLlvm(): String {
        return "sub ${arg.toLlvmType()} 0, ${arg.toLlvm()}"
    }
}

abstract class BinaryOp(result: Int, var left: OpArgument, var right: OpArgument, type: Type): RegistryOp(RegistryArg(result, type)) {
    override fun opToLlvm(): String {
        return "${binaryOpName()} ${left.toLlvmType()} ${left.toLlvm()}, ${right.toLlvm()}"
    }

    abstract fun binaryOpName(): String

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {
        if (left is RegistryArg) {
            val x = replaceMap[(left as RegistryArg).number]
            if (x != null) {
                left = x
            }
        }

        if (right is RegistryArg) {
            val x = replaceMap[(right as RegistryArg).number]
            if (x != null) {
                right = x
            }
        }
    }

    override fun opEquals(otherOp: Op): Boolean {
        val (otherLeft, otherRight) = if (otherOp is BinaryOp) Pair(otherOp.left, otherOp.right) else Pair(null, null)

        return if (this::class == otherOp::class) {
            (otherLeft != null && otherRight != null
                    && left.argEquals(otherLeft) && right.argEquals(otherRight)
                    && signOk(otherOp as BinaryOp))
        } else {
            false
        }
    }

    private fun signOk(op: BinaryOp): Boolean {
        return when (this) {
            is latte.ssaconverter.ssa.AddOp -> op is latte.ssaconverter.ssa.AddOp && op.addOp::class == this.addOp::class
            is MultiplicationOp -> op is MultiplicationOp && op.mulOp::class == this.mulOp::class
            is RelationOp -> op is RelationOp && op.relOp::class == this.relOp::class
            is StringRelationOp -> op is StringRelationOp && op.relOp::class == this.relOp::class
            else -> true
        }
    }

    override fun updateUsed(s: MutableSet<Int>) {
        if (left is RegistryArg) {
            s.add((left as RegistryArg).number)
        }

        if (right is RegistryArg) {
            s.add((right as RegistryArg).number)
        }
    }
}

class AddOp(result: Int, left: OpArgument, right: OpArgument, val addOp: AddOp): BinaryOp(result, left, right, argToType(left)) {
    override fun printOp(): String {
        return "${left.print()} $addOp ${right.print()}"
    }

    override fun binaryOpName(): String {
        return when (addOp) {
            is Plus -> "add"
            is Minus -> "sub"
            else -> throw java.lang.RuntimeException("unknown addOp: $addOp")
        }
    }
}
class OrOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right, Bool()) {
    override fun printOp(): String {
        return "${left.print()} || ${right.print()}"
    }

    override fun binaryOpName(): String {
        return "or"
    }
}
class AndOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right, Bool()) {
    override fun printOp(): String {
        return "${left.print()} && ${right.print()}"
    }

    override fun binaryOpName(): String {
        return "and"
    }
}
class MultiplicationOp(result: Int, left: OpArgument, right: OpArgument, val mulOp: MulOp): BinaryOp(result, left, right, latte.Absyn.Int()) {
    override fun printOp(): String {
        return "${left.print()} $mulOp ${right.print()}"
    }

    override fun binaryOpName(): String {
        return when(mulOp) {
            is Times -> "mul"
            is Div -> "sdiv"
            is Mod -> "srem"
            else -> {
                throw RuntimeException("unknown mulOp: $mulOp")
            }
        }
    }
}
class AddStringOp(result: Int, var left: OpArgument, var right: OpArgument): RegistryOp(RegistryArg(result, Str())) {
    override fun printOp(): String {
        return "${left.print()} ++ ${right.print()}"
    }

    override fun opToLlvm(): String {
        return "call i8* @add.Str(i8* ${left.toLlvm()}, i8* ${right.toLlvm()})"
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {
        if (left is RegistryArg) {
            val x = replaceMap[(left as RegistryArg).number]
            if (x != null) {
                left = x
            }
        }

        if (right is RegistryArg) {
            val x = replaceMap[(right as RegistryArg).number]
            if (x != null) {
                right = x
            }
        }
    }

    override fun opEquals(otherOp: Op): Boolean {
        val x = otherOp is AddStringOp && left.argEquals(otherOp.left) && right.argEquals(otherOp.right)
        return x
    }

    override fun updateUsed(s: MutableSet<Int>) {
        if (left is RegistryArg) {
            s.add((left as RegistryArg).number)
        }

        if (right is RegistryArg) {
            s.add((right as RegistryArg).number)
        }
    }
}

// Bools are represented as i1, so they can be compared like ints, but strings need separate ops.
class RelationOp(result: Int, left: OpArgument, right: OpArgument, val relOp: RelOp): BinaryOp(result, left, right, Bool()) {
    override fun printOp(): String {
        return "${left.print()} $relOp ${right.print()}"
    }

    override fun binaryOpName(): String {
        val relName = when(relOp) {
            is GTH -> "sgt"
            is GE -> "sge"
            is LTH -> "slt"
            is LE -> "sle"
            is EQU -> "eq"
            is NE -> "ne"
            else -> throw RuntimeException("unknown relop: $relOp")
        }
        return "icmp $relName"
    }
}

// TODO: not a binary op (conversion to llvm would fail)
class StringRelationOp(result: Int, left: OpArgument, right: OpArgument, val relOp: RelOp): BinaryOp(result, left, right, Bool()) {
    override fun printOp(): String {
        return "${left.print()} STRCMP $relOp ${right.print()}"
    }

    override fun opToLlvm(): String {
        val relOpCode: Int = when(relOp) {
            is GE -> 1
            is GTH -> 2
            is LTH -> 3
            is LE -> 4
            is EQU -> 5
            is NE -> 6
            else -> {
                System.err.println("unknown relOp: $relOp")
                exitProcess(1)
            }
        }

        return "call i1 @compare.Str(i8* ${left.toLlvm()}, i8* ${right.toLlvm()}, i32 $relOpCode)"
    }

    override fun binaryOpName(): String {
        throw RuntimeException("string relation op should not have binaryOpName defined")
    }
}

class ReturnVoidOp: Op() {
    override fun print() {
        println("return")
    }

    override fun toLlvm(): String {
        return "ret void"
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {}

    override fun opEquals(otherOp: Op): Boolean {
        return false
    }

    override fun getReplacement(): OpArgument {
        throw RuntimeException("return void operations can't be equal")
    }

    override fun updateUsed(s: MutableSet<Int>) {}

    override fun updateAssigned(s: MutableSet<Int>) {}
}

class ReturnOp(val type: Type, var arg: OpArgument): Op() {
    override fun print() {
        println("return ${arg.print()}: ${typeToString(type)}")
    }

    override fun toLlvm(): String {
        return "ret ${typeToLlvm(type)} ${arg.toLlvm()}"
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {
        if (arg is RegistryArg ) {
            val x = replaceMap[(arg as RegistryArg).number]
            if (x != null) {
                arg = x
            }
        }
    }

    override fun opEquals(otherOp: Op): Boolean {
        return false
    }

    override fun getReplacement(): OpArgument {
        throw RuntimeException("return operations can't be equal")
    }

    override fun updateUsed(s: MutableSet<Int>) {
        if (arg is RegistryArg) {
            s.add((arg as RegistryArg).number)
        }
    }

    override fun updateAssigned(s: MutableSet<Int>) {}
}

class IfOp(var cond: OpArgument, val label1: String, val label2: String): Op() {
    override fun print() {
        println("if ${cond.print()} then $label1 else $label2")
    }

    override fun toLlvm(): String {
        return "br ${cond.toLlvmType()} ${cond.toLlvm()}, label %$label1, label %$label2"
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {
        if (cond is RegistryArg) {
            val x = replaceMap[(cond as RegistryArg).number]
            if (x != null) {
                cond = x
            }
        }
    }

    override fun opEquals(otherOp: Op): Boolean {
        return false
    }

    override fun getReplacement(): OpArgument {
        throw RuntimeException("if ops can't be equal")
    }

    override fun updateUsed(s: MutableSet<Int>) {
        if (cond is RegistryArg) {
            s.add((cond as RegistryArg).number)
        }
    }

    override fun updateAssigned(s: MutableSet<Int>) {}
}
class JumpOp(private val label: String): Op() {
    override fun print() {
        println("jump $label")
    }

    override fun toLlvm(): String {
        return "br label %$label"
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {}

    override fun opEquals(otherOp: Op): Boolean {
        return false
    }

    override fun getReplacement(): OpArgument {
        throw RuntimeException("jump op can't be compared")
    }

    override fun updateUsed(s: MutableSet<Int>) {}

    override fun updateAssigned(s: MutableSet<Int>) {}
}

class UnreachableOp: Op() {
    override fun print() {
        println("unreachable")
    }

    override fun toLlvm(): String {
        return "unreachable"
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {}

    override fun opEquals(otherOp: Op): Boolean {
        return false
    }

    override fun getReplacement(): OpArgument {
        throw RuntimeException("unreachable op can't be equal")
    }

    override fun updateUsed(s: MutableSet<Int>) {}

    override fun updateAssigned(s: MutableSet<Int>) {}
}

class StoreOp(val varType: Type, var arg: OpArgument, val loc: Int) : Op() {
    override fun print() {
        println("store ${arg.print()} in %$loc *${typeToString(varType)}")
    }

    override fun toLlvm(): String {
        val t = typeToLlvm(varType)
        return "store $t, $t* ${arg.toLlvm()}"
    }

    override fun reduce(replaceMap: MutableMap<Int, OpArgument>) {
        if (arg is RegistryArg) {
            val r = replaceMap[(arg as RegistryArg).number]
            if (r != null) {
                arg = r
            }
        } else {
            throw RuntimeException("expected object to be in a registry, found $arg instead")
        }
    }

    override fun opEquals(otherOp: Op): Boolean {
        // Store operations modify the state, so can't be compared
        return false
    }

    override fun getReplacement(): OpArgument {
        throw RuntimeException("store operations can't be equal")
    }

    override fun updateUsed(s: MutableSet<Int>) {
        if (arg is RegistryArg) {
            s.add((arg as RegistryArg).number)
        }
    }

    override fun updateAssigned(s: MutableSet<Int>) {
        // Does not assign to anything.
        return
    }
}
