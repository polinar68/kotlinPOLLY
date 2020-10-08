/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.transformers.ensureResolved
import org.jetbrains.kotlin.fir.scopes.FirTypeScope
import org.jetbrains.kotlin.fir.scopes.impl.FirStandardOverrideChecker
import org.jetbrains.kotlin.fir.scopes.impl.FirTypeIntersectionScope
import org.jetbrains.kotlin.fir.scopes.scopeForClass
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.typeContext
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl

fun ConeKotlinType.scope(useSiteSession: FirSession, scopeSession: ScopeSession): FirTypeScope? =
    scope(useSiteSession, scopeSession, FirResolvePhase.SUPER_TYPES)

fun ConeKotlinType.scopeForStatusResolve(useSiteSession: FirSession, scopeSession: ScopeSession): FirTypeScope? =
    scope(useSiteSession, scopeSession, FirResolvePhase.TYPES)

private fun ConeKotlinType.scope(useSiteSession: FirSession, scopeSession: ScopeSession, requiredPhase: FirResolvePhase): FirTypeScope? {
    return when (this) {
        is ConeKotlinErrorType -> null
        is ConeClassLikeType -> {
            val fullyExpandedType = fullyExpandedType(useSiteSession)
            val fir = fullyExpandedType.lookupTag.toSymbol(useSiteSession)?.fir as? FirClass<*> ?: return null

            fir.symbol.ensureResolved(requiredPhase, useSiteSession)

            val substitution = createSubstitution(fir.typeParameters, fullyExpandedType, useSiteSession)

            fir.scopeForClass(substitutorByMap(substitution), useSiteSession, scopeSession)
        }
        is ConeTypeParameterType -> {
            val symbol = lookupTag.toSymbol()
            scopeSession.getOrBuild(symbol, TYPE_PARAMETER_SCOPE_KEY) {
                val intersectionType = ConeTypeIntersector.intersectTypes(
                    useSiteSession.typeContext,
                    symbol.fir.bounds.map { it.coneType }
                )
                intersectionType.scope(useSiteSession, scopeSession, requiredPhase) ?: FirTypeScope.Empty
            }
        }
        is ConeRawType -> lowerBound.scope(useSiteSession, scopeSession, requiredPhase)
        is ConeFlexibleType -> lowerBound.scope(useSiteSession, scopeSession, requiredPhase)
        is ConeIntersectionType -> FirTypeIntersectionScope.prepareIntersectionScope(
            useSiteSession,
            FirStandardOverrideChecker(useSiteSession),
            intersectedTypes.mapNotNullTo(mutableListOf()) {
                it.scope(useSiteSession, scopeSession, requiredPhase)
            }
        )
        is ConeDefinitelyNotNullType -> original.scope(useSiteSession, scopeSession, requiredPhase)
        is ConeIntegerLiteralType -> error("ILT should not be in receiver position")
        else -> null
    }
}

fun FirRegularClass.defaultType(): ConeClassLikeTypeImpl {
    return ConeClassLikeTypeImpl(
        symbol.toLookupTag(),
        typeParameters.map {
            ConeTypeParameterTypeImpl(
                it.symbol.toLookupTag(),
                isNullable = false
            )
        }.toTypedArray(),
        isNullable = false
    )
}

fun FirAnonymousObject.defaultType(): ConeClassLikeType {
    return this.typeRef.coneTypeSafe() ?: ConeClassLikeTypeImpl(
        symbol.toLookupTag(),
        emptyArray(),
        isNullable = false
    )
}

val TYPE_PARAMETER_SCOPE_KEY = scopeSessionKey<FirTypeParameterSymbol, FirTypeScope>()
