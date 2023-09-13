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
*  ServiceConfig.kt
*
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
*/
package com.ailegorreta.cacheservice.config

import com.ailegorreta.resourceserver.security.config.SecurityServiceConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericToStringSerializer
import org.springframework.stereotype.Component


/**
 * Service configuration stored in the properties .yml file.
 *
 * @author rlh
 * @project : cache-service
 * @date September 2023
 *
 */
@Component
@Configuration
class ServiceConfig : SecurityServiceConfig {

    @Value("\${spring.application.name}")
    val appName: String? = null

    @Value("\${cache-service.testing}")
    val testing = false

    @Value("\${security.clientId}")
    val clientId = "false"

    @Value("\${microservice.param.provider-uri}")
    private val paramProviderUri: String = "Issuer uri not defined"
    fun getParamProvider() = paramProviderUri


    override fun getSecurityIAMProvider() = "not needed for this micro.service"

    override fun getSecurityClientId(): HashMap<String, String> {
        return hashMapOf(
            "param-service" to clientId
            /* other microservice providers can be added here */
        )
    }

    override fun getSecurityDefaultClientId() = clientId

    @Value("\${server.port}")
    private val serverPort: Int = 0
    override fun getServerPort() = serverPort

    @Value("\${spring.data.redis.host}")
    private val redisHost: String? = null

    @Value("\${spring.data.redis.port}")
    private val redisPort = 0

    @Value("\${spring.data.redis.password}")
    private val redisPassword: String? = null

}
