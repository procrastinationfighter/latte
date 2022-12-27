package latte.ssaconverter.ssa

import latte.Absyn.ListArg
import latte.Absyn.Type

class SSAFun(val type: Type, val args: ListArg, block: SSABlock)
