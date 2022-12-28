package latte.ssaconverter.ssa

import javax.swing.text.StyledEditorKit.BoldAction

interface OpArgument

class RegistryArg(val number: Int): OpArgument

class IntArg(val number: Int): OpArgument

class BoolArg(val b: Boolean): OpArgument

abstract class Op
abstract class RegistryOp(val result: RegistryArg): Op()

abstract class UnaryOp(result: Int, val arg: OpArgument): RegistryOp(RegistryArg(result))

class NotOp(result: Int, arg: OpArgument): UnaryOp(result, arg)
class NegOp(result: Int, arg: OpArgument): UnaryOp(result, arg)

abstract class BinaryOp(result: Int, left: OpArgument, right: OpArgument): RegistryOp(RegistryArg(result))

class AddOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)
class SubOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)

class ReturnVoidOp: Op()
