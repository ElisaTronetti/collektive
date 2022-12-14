package io.github.elisatronetti

import io.github.elisatronetti.visitors.collectAggregateContextClass
import io.github.elisatronetti.visitors.collectAlignedOnFunction
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * The generation extension is used to register the transformer plugin, which is going to modify
 * the IR using the function responsible for the alignment.
 */
class AlignmentIrGenerationExtension: IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        // Function that is responsible to handle the alignment
        val alignedOnFunction: IrFunction? = collectAlignedOnFunction(moduleFragment)
        // Aggregate Context class that has the reference to the stack
        val aggregateContextClass: IrClass? = collectAggregateContextClass(moduleFragment)
        if (alignedOnFunction != null && aggregateContextClass != null) {
            moduleFragment.transform(
                AggregateCallTransformer(
                    pluginContext,
                    aggregateContextClass,
                    alignedOnFunction
                ),
                null
            )
        } else {
            if (alignedOnFunction == null)
                println("[COMPILER-PLUGIN]: the function that is used to handle the alignment has not been found.")
            if (aggregateContextClass == null)
                println("[COMPILER-PLUGIN]: the aggregate context used to update the stack for " +
                        "alignment has not been found.")
        }
    }
}
