/* Copyright (c) 2023, LegoSoft Soluciones, S.C.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are not permitted.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *  ParamService.kt
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.service

import com.ailegorreta.cacheservice.config.ServiceConfig
import com.ailegorreta.cacheservice.model.*
import com.ailegorreta.cacheservice.util.GraphqlSchemaReaderUtil
import com.ailegorreta.commons.utils.HasLogger
import com.ailegorreta.resourceserver.utils.UserContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono

/**
 * Param service.
 *
 * REST calls to the param-service, i.e., when we need fresh data not stored in Redis o because it has been
 * invalidated.
 *
 * @author rlh
 * @project : cache-service
 * @date September 2023
 *
 */
@Service
class ParamService(@Qualifier("client_credentials_load_balanced")  val webClient: WebClient,
                   // ^ could use @Qualifier("client_credentials_load_balanced) for load balanced calls
                   val serviceConfig: ServiceConfig): HasLogger {
    fun uri() = UriComponentsBuilder.fromUriString(serviceConfig.getParamProvider())

    /**
     * Method that read a systemRate from param microservice. It is called if the systemRate does NOT
     * exist in redis database (i.e., first time or if the systemRate changes and get an event).
     */
    fun getFromParamsSystemRate(name: String): SystemRate? {
        val graphQLRequestBody = GraphqlRequestBody(GraphqlSchemaReaderUtil.getSchemaFromFileName("getSystemRate"),
                                                    mutableMapOf("input" to name))
        val res = webClient.post()
                            .uri(uri().path("/param/graphql").build().toUri())
                            .accept(MediaType.APPLICATION_JSON)
                            .body(Mono.just(graphQLRequestBody), GraphqlRequestBody::class.java)
                            .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId(serviceConfig.clientId + "-client-credentials"))
                            .retrieve()
                            .bodyToMono(GraphqlResponseGetSystemRate::class.java)
                            .block()

        return res!!.data.systemRate
    }

    /**
     * Reads all systemDates from param microservice. Just the first time or when a systemDate is modified
     */
    fun allSystemDates(): List<SystemDate> {
        val graphQLRequestBody = GraphqlRequestBody(GraphqlSchemaReaderUtil.getSchemaFromFileName("allSystemDates"))

        val res = webClient.post()
                            .uri(uri().path("/param/graphql").build().toUri())
                            .accept(MediaType.APPLICATION_JSON)
                            .header(UserContext.CORRELATION_ID, UserContext.getCorrelationId())
                            .body(Mono.just(graphQLRequestBody), GraphqlRequestBody::class.java)
                            .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId(serviceConfig.clientId + "-client-credentials"))
                            .retrieve()
                            .bodyToMono(GraphqlResponseSystemDates::class.java)
                            .block()

        if (res == null) {
            logger.error("No se pudo leer la fecha del día de hoy el micro servicio param")

            return emptyList()
        }
        return res.data.systemDates
    }

    /**
     * Reads all document types from param microservice. Just the first time or when a documentType is modified
     */
    fun allDocumentTypes(): List<DocumentType> {
        val graphQLRequestBody = GraphqlRequestBody(GraphqlSchemaReaderUtil.getSchemaFromFileName("allDocumentTypes"))
        val res = webClient.post()
                            .uri(uri().path("/param/graphql").build().toUri())
                            .accept(MediaType.APPLICATION_JSON)
                            .body(Mono.just(graphQLRequestBody), GraphqlRequestBody::class.java)
                            .attributes(ServerOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId(serviceConfig.clientId + "-client-credentials"))
                            .retrieve()
                            .bodyToMono(GraphqlResponseDocumentTypes::class.java)
                            .block()

        return res!!.data.documentTypes
    }
}