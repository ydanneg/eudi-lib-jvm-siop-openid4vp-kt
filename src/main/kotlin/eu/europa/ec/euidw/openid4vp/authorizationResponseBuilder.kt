package eu.europa.ec.euidw.openid4vp

import com.nimbusds.jwt.JWT
import eu.europa.ec.euidw.openid4vp.internal.DefaultAuthorizationResponseBuilder
import eu.europa.ec.euidw.prex.PresentationSubmission

sealed interface AuthorizationResponse {
    data class DirectPost(val responseUri: HttpsUrl, val data: AuthorizationResponseData) : AuthorizationResponse
    data class DirectPostJwt(val responseUri: HttpsUrl, val data: AuthorizationResponseData) : AuthorizationResponse
    data class Query(val redirectUri: HttpsUrl, val data: AuthorizationResponseData) : AuthorizationResponse
    data class QueryJwt(val redirectUri: HttpsUrl, val data: AuthorizationResponseData) : AuthorizationResponse
    data class Fragment(val redirectUri: HttpsUrl, val data: AuthorizationResponseData) : AuthorizationResponse
    data class FragmentJwt(val redirectUri: HttpsUrl, val data: AuthorizationResponseData) : AuthorizationResponse
}


sealed interface AuthorizationResponseData {

    val state : String

    sealed interface Success : AuthorizationResponseData

    data class IdTokenResponseData(
        val idToken: JWT,
        override val state : String
    ) : Success

    data class VPTokenResponseData(
        val verifiableCredential: List<Jwt>,
        val presentationSubmission: PresentationSubmission,
        override val state : String
    ) : Success

    data class IdAndVPTokenResponseData(
        val idToken: JWT,
        val verifiableCredential: List<Jwt>,
        val presentationSubmission: PresentationSubmission,
        override val state : String
    ) : Success

    sealed interface Failed : AuthorizationResponseData
    data class InvalidRequest(val error: RequestValidationError, override val state : String) : Failed
    data class FailedToResolveRequest(val error: ResolutionError, override val state : String) : Failed
    data class InvalidUrl(val url: String, override val state : String) : Failed
    data class UserRejection(val rejectionMessage: String, override val state : String) : Failed
    data class NoConsensusResponseData(val reason: String, override val state : String ) : Failed

}


interface AuthorizationResponseBuilder {

    suspend fun buildResponse(
        requestObject: ResolvedRequestObject,
        consensus: Consensus
    ): AuthorizationResponse

    companion object {
        fun make(walletOpenId4VPConfig: WalletOpenId4VPConfig): AuthorizationResponseBuilder =
            DefaultAuthorizationResponseBuilder(walletOpenId4VPConfig)
    }
}

