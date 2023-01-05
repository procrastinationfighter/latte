package latte.ssaconverter.ssa

import latte.Absyn.AddOp
import latte.Absyn.MulOp
import latte.Absyn.RelOp
import latte.Absyn.Type
import latte.common.typeToString
import javax.swing.text.StyledEditorKit.BoldAction

interface OpArgument {
    fun print(): String
    fun toLlvm(): String
}

class RegistryArg(val number: Int): OpArgument {
    override fun print(): String {
        return "%$number"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

class IntArg(val number: Int): OpArgument {
    override fun print(): String {
        return "$number"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

class BoolArg(val b: Boolean): OpArgument {
    override fun print(): String {
        return "$b"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

class StringArg(val s: String): OpArgument {
    override fun print(): String {
        return "\"$s\""
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
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
}

class PhiOp(val phi: Phi): RegistryOp(RegistryArg(phi.registry)) {
    override fun printOp(): String {
        return phi.values.map { "${it.key}: ${it.value.print()}" }.joinToString(prefix = "phi[", separator = ", ", postfix = "]")
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

class AppOp(result: Int, val name: String, val type: Type, val args: List<OpArgument>): RegistryOp(RegistryArg(result)) {
    override fun printOp(): String {
        val argsStr = args.joinToString(separator = ", ") { it.print() }
        return "call $name ($argsStr): ${typeToString(type)}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

abstract class UnaryOp(result: Int, val arg: OpArgument): RegistryOp(RegistryArg(result))

class NotOp(result: Int, arg: OpArgument): UnaryOp(result, arg) {
    override fun printOp(): String {
        return "not ${arg.print()}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}
class NegOp(result: Int, arg: OpArgument): UnaryOp(result, arg) {
    override fun printOp(): String {
        return "neg ${arg.print()}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}
class AssignOp(result: Int, arg: OpArgument): UnaryOp(result, arg) {
    override fun printOp(): String {
        return arg.print()
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

abstract class BinaryOp(result: Int, left: OpArgument, right: OpArgument): RegistryOp(RegistryArg(result))

class AddOp(result: Int, val left: OpArgument, val right: OpArgument, val addOp: AddOp): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} $addOp ${right.print()}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}
class OrOp(result: Int, val left: OpArgument, val right: OpArgument): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} || ${right.print()}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}
class AndOp(result: Int, val left: OpArgument, val right: OpArgument): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} && ${right.print()}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}
class MultiplicationOp(result: Int, val left: OpArgument, val right: OpArgument, val mulOp: MulOp): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} $mulOp ${right.print()}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}
class AddStringOp(result: Int, val left: OpArgument, val right: OpArgument): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} ++ ${right.print()}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

// Bools are represented as i1, so they can be compared like ints, but strings need separate ops.
class RelationOp(result: Int, val left: OpArgument, val right: OpArgument, val relOp: RelOp): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} $relOp ${right.print()}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

class StringRelationOp(result: Int, val left: OpArgument, val right: OpArgument, val relOp: RelOp): BinaryOp(result, left, right) {
    override fun printOp(): String {
        return "${left.print()} STRCMP $relOp ${right.print()}"
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

class ReturnVoidOp: Op() {
    override fun print() {
        println("return")
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

class ReturnOp(val type: Type, val arg: OpArgument): Op() {
    override fun print() {
        println("return ${arg.print()}: ${typeToString(type)}")
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}

class IfOp(val cond: OpArgument, val label1: String, val label2: String): Op() {
    override fun print() {
        println("if ${cond.print()} then $label1 else $label2")
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}
class JumpOp(val label: String): Op() {
    override fun print() {
        println("jump $label")
    }

    override fun toLlvm(): String {
        TODO("Not yet implemented")
    }
}
