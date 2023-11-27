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
package eu.europa.ec.eudi.openid4vp

/**
 * An interface providing support for handling
 * an OAUTH2 authorization request that represents
 * either an SIOP authentication request, or an OpenId4VP authorization request or
 * a combined SIOP & OpenId4VP request
 *
 * The support is grouped into three groups:
 * [validate & resolve][AuthorizationRequestResolver]
 * [build response][AuthorizationResponseBuilder]
 * [dispatch response][Dispatcher]
 *
 * @see AuthorizationRequestResolver
 * @see AuthorizationResponseBuilder
 * @see Dispatcher
 */
interface SiopOpenId4Vp : AuthorizationRequestResolver, AuthorizationResponseBuilder, Dispatcher {

    companion object {

        /**
         * Factory method to create a [SiopOpenId4Vp].
         *
         * @param walletOpenId4VPConfig wallet's configuration
         * @param httpClientFactory a factory to obtain a Ktor http client
         * @return a [SiopOpenId4Vp]
         */
        @Deprecated(
            message = "Will be removed. Use invoke instead.",
            replaceWith = ReplaceWith("SiopOpenId4Vp(walletOpenId4VPConfig, httpClientFactory)"),
        )
        fun ktor(
            walletOpenId4VPConfig: WalletOpenId4VPConfig,
            httpClientFactory: KtorHttpClientFactory = DefaultHttpClientFactory,
        ): SiopOpenId4Vp = SiopOpenId4Vp(walletOpenId4VPConfig, httpClientFactory)

        /**
         * Factory method to create a [SiopOpenId4Vp].
         *
         * @param walletOpenId4VPConfig wallet's configuration
         * @param httpClientFactory a factory to obtain a Ktor http client
         * @return a [SiopOpenId4Vp]
         */
        operator fun invoke(
            walletOpenId4VPConfig: WalletOpenId4VPConfig,
            httpClientFactory: KtorHttpClientFactory = DefaultHttpClientFactory,
        ): SiopOpenId4Vp =
            SiopOpenId4Vp(
                AuthorizationRequestResolver(httpClientFactory, walletOpenId4VPConfig),
                Dispatcher(httpClientFactory),
                AuthorizationResponseBuilder(walletOpenId4VPConfig),
            )

        /**
         * Factory method to create a [SiopOpenId4Vp].
         *
         * @param authorizationRequestResolver the [AuthorizationRequestResolver] instance to use
         * @param dispatcher the [Dispatcher] instance to use
         * @param authorizationResponseBuilder the [AuthorizationResponseBuilder] instance to use
         * @return a [SiopOpenId4Vp]
         */
        operator fun invoke(
            authorizationRequestResolver: AuthorizationRequestResolver,
            dispatcher: Dispatcher,
            authorizationResponseBuilder: AuthorizationResponseBuilder,
        ): SiopOpenId4Vp =
            object :
                SiopOpenId4Vp,
                AuthorizationRequestResolver by authorizationRequestResolver,
                Dispatcher by dispatcher,
                AuthorizationResponseBuilder by authorizationResponseBuilder {}
    }
}
