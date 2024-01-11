/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.openid4vp.internal.response

import com.nimbusds.jose.*
import com.nimbusds.jose.JWEAlgorithm.Family
import com.nimbusds.jose.crypto.ECDHEncrypter
import com.nimbusds.jose.crypto.RSAEncrypter
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.id.Issuer
import eu.europa.ec.eudi.openid4vp.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.jvm.Throws

/**
 * Creates according to the [verifier's requirements][jarmRequirement], a JARM JWT which encapsulates
 * the provided [data]
 *
 * @param data the response to be sent to the verifier
 * @receiver the wallet configuration
 * @return a JWT containing the [data] which depending on the [jarmRequirement] it could be
 * - a signed JWT
 * - an encrypted JWT
 * - an encrypted JWT containing a signed JWT
 *
 * @throws IllegalStateException in case the wallet configuration doesn't support the [jarmRequirement]
 * @throws JOSEException
 */
@Throws(IllegalStateException::class, JOSEException::class)
internal fun SiopOpenId4VPConfig.jarmJwt(
    jarmRequirement: JarmRequirement,
    data: AuthorizationResponsePayload,
): Jwt = when (jarmRequirement) {
    is JarmRequirement.Signed -> sign(jarmRequirement, data)
    is JarmRequirement.Encrypted -> encrypt(jarmRequirement, data)
    is JarmRequirement.SignedAndEncrypted -> signAndEncrypt(jarmRequirement, data)
}.serialize()

private fun SiopOpenId4VPConfig.sign(
    requirement: JarmRequirement.Signed,
    data: AuthorizationResponsePayload,
): SignedJWT {
    val signingCfg = jarmConfiguration.signingConfig()
    checkNotNull(signingCfg) { "Wallet doesn't support signing JARM" }

    val header = JWSHeader.Builder(requirement.responseSigningAlg)
        .keyID(signingCfg.signer.getKeyId())
        .build()

    val claimSet = JwtPayloadFactory.create(data, issuer, Instant.now())
    return SignedJWT(header, claimSet).apply { sign(signingCfg.signer) }
}

private fun SiopOpenId4VPConfig.encrypt(
    requirement: JarmRequirement.Encrypted,
    data: AuthorizationResponsePayload,
): EncryptedJWT {
    val encryptionCfg = jarmConfiguration.encryptionConfig()
    checkNotNull(encryptionCfg) { "Wallet doesn't support encrypted JARM" }

    val (jweAlgorithm, encryptionMethod, encryptionKeySet) = requirement
    val jweEncrypter = keyAndEncryptor(jweAlgorithm, encryptionKeySet)
    val jweHeader = JWEHeader(jweAlgorithm, encryptionMethod)
    val claimSet = JwtPayloadFactory.create(data, issuer, Instant.now())
    return EncryptedJWT(jweHeader, claimSet).apply { encrypt(jweEncrypter) }
}

private fun SiopOpenId4VPConfig.signAndEncrypt(
    requirement: JarmRequirement.SignedAndEncrypted,
    data: AuthorizationResponsePayload,
): JWEObject {
    check(jarmConfiguration is JarmConfiguration.SigningAndEncryption) {
        "Wallet doesn't signing & encrypting JARM"
    }

    val signedJwt = sign(requirement.signed, data)
    val (jweAlgorithm, encryptionMethod, encryptionKeySet) = requirement.encryptResponse
    val jweEncrypter = keyAndEncryptor(jweAlgorithm, encryptionKeySet)
    return JWEObject(
        JWEHeader(jweAlgorithm, encryptionMethod),
        Payload(signedJwt),
    ).apply { encrypt(jweEncrypter) }
}

private fun keyAndEncryptor(
    jweAlgorithm: JWEAlgorithm,
    jwkSet: JWKSet,
): JWEEncrypter = EncrypterFactory.findEncrypters(jweAlgorithm, jwkSet)
    .firstNotNullOfOrNull { it.value }
    ?: error("Cannot find appropriate encryption key for ${jweAlgorithm.name}")

private object JwtPayloadFactory {

    private const val PRESENTATION_SUBMISSION_CLAIM = "presentation_submission"
    private const val VP_TOKEN_CLAIM = "vp_token"
    private const val STATE_CLAIM = "state"
    private const val ID_TOKEN_CLAIM = "id_token"
    private const val ERROR_CLAIM = "error"
    private const val ERROR_DESCRIPTION_CLAIM = "error_description"
    fun create(data: AuthorizationResponsePayload, issuer: Issuer?, issuedAt: Instant): JWTClaimsSet =
        buildJsonObject {
            issuer?.let { put("iss", it.value) }
            put("iat", issuedAt.epochSecond)
            put("aud", data.clientId)
            put(STATE_CLAIM, data.state)

            when (data) {
                is AuthorizationResponsePayload.SiopAuthentication -> {
                    put(ID_TOKEN_CLAIM, data.idToken)
                }

                is AuthorizationResponsePayload.OpenId4VPAuthorization -> {
                    put(VP_TOKEN_CLAIM, data.vpToken)
                    put(PRESENTATION_SUBMISSION_CLAIM, Json.encodeToJsonElement(data.presentationSubmission))
                }

                is AuthorizationResponsePayload.SiopOpenId4VPAuthentication -> {
                    put(ID_TOKEN_CLAIM, data.idToken)
                    put(VP_TOKEN_CLAIM, data.vpToken)
                    put(PRESENTATION_SUBMISSION_CLAIM, Json.encodeToJsonElement(data.presentationSubmission))
                }

                is AuthorizationResponsePayload.InvalidRequest -> {
                    put(ERROR_CLAIM, AuthorizationRequestErrorCode.fromError(data.error).code)
                    put(ERROR_DESCRIPTION_CLAIM, "${data.error}")
                }

                is AuthorizationResponsePayload.NoConsensusResponseData -> {
                    put(ERROR_CLAIM, AuthorizationRequestErrorCode.USER_CANCELLED.code)
                }
            }
        }.asJWTClaimSet()

    private fun JsonObject.asJWTClaimSet(): JWTClaimsSet {
        val jsonStr = Json.encodeToString(this)
        return JWTClaimsSet.parse(jsonStr)
    }
}

private object EncrypterFactory {

    fun findEncrypters(
        algorithm: JWEAlgorithm,
        keySet: JWKSet,
    ): Map<JWK, JWEEncrypter> {
        fun encrypter(key: JWK) = runCatching {
            createEncrypter(key, algorithm)
        }.getOrNull()

        return keySet.keys.mapNotNull { key ->
            encrypter(key)?.let { encrypter -> key to encrypter }
        }.toMap()
    }

    fun createEncrypter(
        key: JWK,
        algorithm: JWEAlgorithm,
    ): JWEEncrypter? =
        familyOf(algorithm)?.let { family ->
            when {
                family == Family.ECDH_ES && key is ECKey -> ECDHEncrypter(key)
                family == Family.RSA && key is RSAKey -> RSAEncrypter(key)
                else -> null
            }
        }

    private val SupportedFamilies = listOf(Family.ECDH_ES, Family.RSA)
    private fun familyOf(algorithm: JWEAlgorithm): Family? =
        SupportedFamilies.firstOrNull { family -> family.contains(algorithm) }
}