package latte.ssaconverter.ssa

import latte.Absyn.Type
import java.util.*

class SSAClass(val name: String, val variables: Map<String, Type>, val parentClass: Optional<SSAClass>, val types: String, val order: Map<String, Int>) {
    fun varsToLlvm(): String {
        return if (parentClass.isPresent) {
            "${parentClass.get().varsToLlvm()}, $types"
        } else {
            types
        }
    }
}
