package latte.ssaconverter.ssa

import latte.Absyn.*
import latte.Absyn.AddOp
import latte.common.typeToString
import latte.llvmconverter.typeToLlvm
import latte.ssaconverter.argToType
import javax.swing.text.StyledEditorKit.BoldAction
import kotlin.system.exitProcess

interface OpArgument {
    fun print(): String
    fun toLlvm(): String
    fun toLlvmType(): String
}

class RegistryArg(val number: Int, val type: Type): OpArgument {
    override fun print(): String {
        return "%$number"
    }

    override fun toLlvm(): String {
        return "%${number}"
    }

    override fun toLlvmType(): String {
        return typeToLlvm(type)
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
}

abstract class Op {
    abstract fun print()
    abstract fun toLlvm(): String
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
            "%${result.number} = ${opToLlvm()}"
        }
    }

    abstract fun opToLlvm(): String;
}

class PhiOp(val phi: Phi): RegistryOp(RegistryArg(phi.registry, phi.getType())) {
    override fun printOp(): String {
        return phi.values.map { "${it.key}: ${it.value.print()}" }.joinToString(prefix = "phi[", separator = ", ", postfix = "]")
    }

    override fun opToLlvm(): String {
        val branches = phi.values.map { "[${it.value.toLlvm()}, %${it.key}]" }.joinToString(separator = ", ")
        return "phi ${phi.getType()} $branches"
    }
}

class AppOp(result: Int, val name: String, val type: Type, val args: List<OpArgument>): RegistryOp(RegistryArg(result, type)) {
    override fun printOp(): String {
        val argsStr = args.joinToString(separator = ", ") { it.print() }
        return "call $name ($argsStr): ${typeToString(type)}"
    }

    override fun opToLlvm(): String {
        val argsStr = args.joinToString(separator = ", ") { "${typeToLlvm(argToType(it))} ${it.toLlvm()}" }
        return "call ${typeToLlvm(type)} @$name($argsStr)"
    }
}

abstract class UnaryOp(result: Int, val arg: OpArgument, type: Type): RegistryOp(RegistryArg(result, type))

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

abstract class BinaryOp(result: Int, val left: OpArgument, val right: OpArgument, type: Type): RegistryOp(RegistryArg(result, type)) {
    override fun opToLlvm(): String {
        return "${binaryOpName()} ${left.toLlvmType()} ${left.toLlvm()}, ${right.toLlvm()}"
    }

    abstract fun binaryOpName(): String
}

class AddOp(result: Int, left: OpArgument, right: OpArgument, val addOp: AddOp): BinaryOp(result, left, right, argToType(left)) {
    override fun printOp(): String {
        return "${left.print()} $addOp ${right.print()}"
    }

    override fun binaryOpName(): String {
        return "add"
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
class AddStringOp(result: Int, val left: OpArgument, val right: OpArgument): RegistryOp(RegistryArg(result, Str())) {
    override fun printOp(): String {
        return "${left.print()} ++ ${right.print()}"
    }

    override fun opToLlvm(): String {
        return "call i8* @addStr(i8* ${left.toLlvm()}, i8* ${right.toLlvm()})"
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
        return "add $relName"
    }
}

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

        return "call i32 @compareStr(i8* ${left.toLlvm()}, i8* ${right.toLlvm()}, i32 $relOpCode)"
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
        return "ret"
    }
}

class ReturnOp(val type: Type, val arg: OpArgument): Op() {
    override fun print() {
        println("return ${arg.print()}: ${typeToString(type)}")
    }

    override fun toLlvm(): String {
        return "ret ${typeToLlvm(type)} ${arg.toLlvm()}"
    }
}

class IfOp(val cond: OpArgument, val label1: String, val label2: String): Op() {
    override fun print() {
        println("if ${cond.print()} then $label1 else $label2")
    }

    override fun toLlvm(): String {
        return "br ${cond.toLlvmType()}, label $label1, label $label2"
    }
}
class JumpOp(val label: String): Op() {
    override fun print() {
        println("jump $label")
    }

    override fun toLlvm(): String {
        return "br label $label"
    }
}
