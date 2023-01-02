package latte.ssaconverter.ssa

import latte.Absyn.RelOp
import latte.Absyn.Type
import javax.swing.text.StyledEditorKit.BoldAction

interface OpArgument

class RegistryArg(val number: Int): OpArgument

class IntArg(val number: Int): OpArgument

class BoolArg(val b: Boolean): OpArgument

class StringArg(val s: String): OpArgument

abstract class Op
abstract class RegistryOp(val result: RegistryArg): Op()

class AppOp(result: Int, val type: Type, val args: List<OpArgument>): RegistryOp(RegistryArg(result))

abstract class UnaryOp(result: Int, val arg: OpArgument): RegistryOp(RegistryArg(result))

class NotOp(result: Int, arg: OpArgument): UnaryOp(result, arg)
class NegOp(result: Int, arg: OpArgument): UnaryOp(result, arg)

abstract class BinaryOp(result: Int, left: OpArgument, right: OpArgument): RegistryOp(RegistryArg(result))

class AddOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)
class SubOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)
class OrOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)
class AndOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)
class MultiplicationOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)
class AddStringOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)

class RelationOp(result: Int, left: OpArgument, right: OpArgument, val relOp: RelOp): BinaryOp(result, left, right)

class ReturnVoidOp: Op()
