package latte.ssaconverter.ssa

interface OpArgument

class RegistryArg(val number: Int): OpArgument

class IntArg(val number: Int): OpArgument

abstract class Op
abstract class RegistryOp(val result: RegistryArg): Op()

abstract class UnaryOp(result: Int, val arg: OpArgument): RegistryOp(RegistryArg(result))

abstract class BinaryOp(result: Int, left: OpArgument, right: OpArgument): RegistryOp(RegistryArg(result))

class AddOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)
class SubOp(result: Int, left: OpArgument, right: OpArgument): BinaryOp(result, left, right)

class ReturnVoidOp: Op()
