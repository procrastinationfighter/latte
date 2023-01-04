package latte.ssaconverter.ssa

import latte.Absyn.AddOp
import latte.Absyn.MulOp
import latte.Absyn.RelOp
import latte.Absyn.Type
import latte.common.typeToString
import javax.swing.text.StyledEditorKit.BoldAction

interface OpArgument {
    fun print(): String
}

class RegistryArg(val number: Int): OpArgument {
    override fun print(): String {
        return "%$number"
    }
}

class IntArg(val number: Int): OpArgument {
    override fun print(): String {
        return "$number"
    }
}

class BoolArg(val b: Boolean): OpArgument {
    override fun print(): String {
        return "$b"
    }
}

class StringArg(val s: String): OpArgument {
    override fun print(): String {
        return "\"$s\""
    }
}

abstract class Op {
    abstract fun print()
}
abstract class RegistryOp(val result: RegistryArg): Op() {
    override fun print() {
        println("%${result.number} = ${printOp()}")
    }

    abstract fun printOp(): String
}

class PhiOp(val phi: Phi): RegistryOp(RegistryArg(phi.registry)) {
    override fun printOp(): String {
        return phi.values.map { "${it.key}: ${it.value.print()}" }.joinToString(prefix = "[", separator = ", ", postfix = "]")
    }
}

class AppOp(result: Int, val name: String, val type: Type, val args: List<OpArgument>): RegistryOp(RegistryArg(result)) {
    override fun printOp(): String {
        val argsStr = args.joinToString(separator = ", ") { it.print() }
        return "call $name ($argsStr): ${typeToString(type)}"
    }
}

abstract class UnaryOp(result: Int, val arg: OpArgument): RegistryOp(RegistryArg(result))

class NotOp(result: Int, arg: OpArgument): UnaryOp(result, arg) {
    override fun printOp(): String {
        return "not ${arg.print()}"
    }
}
class NegOp(result: Int, arg: OpArgument): UnaryOp(result, arg) {
    override fun printOp(): String {
        return "neg ${arg.print()}"
    }
}
class AssignOp(result: Int, arg: OpArgument): UnaryOp(result, arg) {
    override fun printOp(): String {
        return arg.print()
    }
}

abstract class BinaryOp(result: Int, left: OpArgument, right: OpArgument): RegistryOp(RegistryArg(result))

class AddOp(result: Int, val left: OpArgument, val right: OpArgument, val addOp: AddOp): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} $addOp ${right.print()}"
    }
}
class OrOp(result: Int, val left: OpArgument, val right: OpArgument): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} || ${right.print()}"
    }
}
class AndOp(result: Int, val left: OpArgument, val right: OpArgument): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} && ${right.print()}"
    }
}
class MultiplicationOp(result: Int, val left: OpArgument, val right: OpArgument, val mulOp: MulOp): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} $mulOp ${right.print()}"
    }
}
class AddStringOp(result: Int, val left: OpArgument, val right: OpArgument): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} ++ ${right.print()}"
    }
}

class RelationOp(result: Int, val left: OpArgument, val right: OpArgument, val relOp: RelOp): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} $relOp ${right.print()}"
    }
}

class ReturnVoidOp: Op() {
    override fun print() {
        println("return")
    }
}

class ReturnOp(val type: Type, val arg: OpArgument): Op() {
    override fun print() {
        println("return ${arg.print()}: ${typeToString(type)}")
    }
}

class IfOp(val cond: OpArgument, val label1: String, val label2: String): Op() {
    override fun print() {
        println("if ${cond.print()} then $label1 else $label2")
    }
}
class JumpOp(val label: String): Op() {
    override fun print() {
        println("jump $label")
    }
}
