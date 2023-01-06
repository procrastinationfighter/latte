package latte.ssaconverter.ssa

import latte.Absyn.Type
import latte.ssaconverter.argToType

class Phi(val variable: String, val registry: Int, val values: Map<String, OpArgument>) {
    fun getType(): Type {
        values.map { return argToType(it.value) }

        return latte.Absyn.Void()
    }
}
