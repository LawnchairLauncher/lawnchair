package app.lawnchair.search.algorithms.data.calculator.internal

import java.math.BigDecimal

abstract class Function {

    abstract fun call(arguments: List<BigDecimal>): BigDecimal

}
