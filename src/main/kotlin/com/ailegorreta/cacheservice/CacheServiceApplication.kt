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
 *  CacheServiceApplication.kt
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder


/**
 * Cache service.
 *
 * All first REST calls are forwarded to the source microservice and stored in redis memory database for performance
 * Subsequent calls are read from Redis memory database.
 *
 * To keep Redis database in sync, the cache microservice listen to event from the source microservice. Is some event
 * is read, then the redis database is invalidated.
 *
 * @author rlh
 * @project : cache-service
 * @date July 2023
 *
 */
@SpringBootApplication
@ComponentScan(basePackages = ["com.ailegorreta.cacheservice", "com.ailegorreta.resourceserver"])
								// ^ this package must be included in order to instantiate de UserContext

class CacheServiceApplication {
	companion object {
		@JvmStatic
		fun main(args: Array<String>) {
			SpringApplication.run(CacheServiceApplication::class.java, *args)
		}

		@Bean
		fun kotlinPropertyConfigurer(): PropertySourcesPlaceholderConfigurer {
			val propertyConfigurer = PropertySourcesPlaceholderConfigurer()

			propertyConfigurer.setPlaceholderPrefix("@{")
			propertyConfigurer.setPlaceholderSuffix("}")
			propertyConfigurer.setIgnoreUnresolvablePlaceholders(true)

			return propertyConfigurer
		}

		@Bean
		fun defaultPropertyConfigurer() = PropertySourcesPlaceholderConfigurer()

	}
	@Bean
	fun mapperConfigurer() = Jackson2ObjectMapperBuilder().apply {
		serializationInclusion(JsonInclude.Include.NON_NULL)
		failOnUnknownProperties(true)
		featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		indentOutput(true)
		modules(listOf(KotlinModule.Builder().build(), JavaTimeModule(), Jdk8Module()))
	}

}