/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.ir

import org.jetbrains.kotlin.fir.backend.Fir2IrComponents
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.psi2ir.PsiSourceManager
import org.jetbrains.kotlin.test.model.BackendFacade
import org.jetbrains.kotlin.test.model.ResultingArtifact

// IR backend (JVM, JS, Native)
class IrBackendInitialInfo(
    val irModuleFragment: IrModuleFragment,
    val symbolTable: SymbolTable,
    val sourceManager: PsiSourceManager,
    val components: Fir2IrComponents
) : ResultingArtifact.BackendInitialInfo()

/**
 * Will have one implementation for KLib and three implementations for different binaries
 */
abstract class IrBackendFacade<out R : ResultingArtifact.Binary> : BackendFacade<IrBackendInitialInfo, R>()
