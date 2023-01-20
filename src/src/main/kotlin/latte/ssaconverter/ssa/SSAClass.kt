package latte.ssaconverter.ssa

import latte.Absyn.Type
import latte.llvmconverter.typeToLlvm
import java.util.*

class SSAClass(val variables: Map<String, Type>, val parentClass: Optional<SSAClass>) {
    fun varsToLlvm(): String {
        val types = variables.values.joinToString(separator = ", ") { typeToLlvm(it) }

        return if (parentClass.isPresent) {
            "${parentClass.get().varsToLlvm()}, $types"
        } else {
            types
        }
    }
}
