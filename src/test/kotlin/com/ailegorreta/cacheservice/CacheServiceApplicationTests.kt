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
 *  CacheServiceApplicationTests.kt
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice

import com.ailegorreta.cacheservice.config.EventConfig
import com.ailegorreta.cacheservice.service.EventService
import com.ailegorreta.cacheservice.service.EventServiceTest
import com.ailegorreta.commons.event.EventDTO
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.*
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * For a good test slices for testing @SpringBootTest, see:
 * https://reflectoring.io/spring-boot-test/
 * https://www.diffblue.com/blog/java/software%20development/testing/spring-boot-test-slices-overview-and-usage/
 *
 * This class test all context with @SpringBootTest annotation and checks that everything is loaded correctly.
 * Also creates the classes needed for all slices in @TestConfiguration annotation
 *
 * Testcontainers:
 *
 * Use for test containers Redis & Kafka following the next's ticks:
 *
 * - As little overhead as possible:
 * - Containers are started only once for all tests
 * - Containers are started in parallel
 * - No requirements for test inheritance
 * - Declarative usage.
 *
 * see article: https://maciejwalkowiak.com/blog/testcontainers-spring-boot-setup/
 *
 * For GraphQL tester see:
 * https://piotrminkowski.com/2023/01/18/an-advanced-graphql-with-spring-boot/
 *
 * Also for a problem with bootstrapServerProperty
 * see: https://blog.mimacom.com/embeddedkafka-kafka-auto-configure-springboottest-bootstrapserversproperty/
 *
 * @author rlh
 * @project : cache-service
 * @date September 2023
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers
/* ^ This is a custom annotation to load the containers */
@ExtendWith(MockitoExtension::class)
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
/* ^ this is because: https://blog.mimacom.com/embeddedkafka-kafka-auto-configure-springboottest-bootstrapserversproperty/ */
@ActiveProfiles("integration-tests")
@DirtiesContext				/* will make sure this context is cleaned and reset between different tests */
class CacheServiceApplicationTests {
	/* StreamBridge instance is used by EventService but in @Test mode it is not instanciated, so we need to mock it:
       see: https://stackoverflow.com/questions/67276613/streambridge-final-cannot-be-mocked
       StreamBridge is a final class, With Mockito2 we can mock the final class, but by default this feature is disabled
       and that need to enable with below steps:

       1. Create a directory ‘mockito-extensions’ in src/test/resources/ folder.
       2. Create a file ‘org.mockito.plugins.MockMaker’ in ‘src/test/resources/mockito-extensions/’ directory.
       3. Write the content 'mock-maker-inline' in org.mockito.plugins.MockMaker file.

        At test class level use ‘@ExtendWith(MockitoExtension.class)’
        Then StreamBridge will be mocked successfully.

        note: Instead of mocking the final class (which is possible with the latest versions of mockito using the
        mock-maker-inline extension), you can wrap StreamBridge into your class and use it in your business logic.
        This way, you can mock and test it any way you need.

        This is a common practice for writing unit tests for code where some dependencies are final or static classes
     */
	@MockBean
	private val streamBridge: StreamBridge? = null
	@MockBean
	private var reactiveJwtDecoder: ReactiveJwtDecoder? = null			// Mocked the security JWT

	/**
	 * Do not use @MockBean because it initialises an empty ReactiveClientRegistration
	 * Repository, so it crashed when create a WebClient.
	 */
	@Autowired
	lateinit var reactiveClientRegistrationRepository: ReactiveClientRegistrationRepository

	@MockBean
	var authorizedClientRepository: OAuth2AuthorizedClientRepository?= null

	/**
	 * For Kafka all are consumer events, the producer we use Kafka template instance
	 */
	@Autowired
	var consumer: EventService? = null

	@Autowired
	var template: KafkaTemplate<String, EventDTO>? = null

	@Autowired
	var producer: EventServiceTest? = null		// EventServiceTest producer that emulates den a message

	@Autowired
	var mapper: ObjectMapper? = null

	@Autowired
	var eventConfig: EventConfig? = null

	@Autowired
	var webClient: WebTestClient? = null

	/**
	 * Context load testing
	 */
	@Test
	// @WithMockUser("spring")
	fun contextLoads() {
		println("[A] ReactiveClientRegistrationRepository:${reactiveClientRegistrationRepository}")
		println("[B] OAuth2AuthorizedClientRepository:${authorizedClientRepository}")
		println("[C] Kafka consumer:${consumer}")
		println("[D] Kafka template:${template}")
		println("[E] Event config: ${eventConfig}")
	}

	@Test
	@Throws(Exception::class)
	fun indexWhenUnAuthenticatedThenRedirect() {
		// @formatter:off
		webClient!!.get()
					.uri("/")
					.exchange()
					.expectStatus().is3xxRedirection()
		// @formatter:on
	}

	@Test
	@WithMockUser
	@Throws(Exception::class)
	fun indexWhenAuthenticatedThenOk() {
		// @formatter:off
		webClient!!.get()
			.uri("/")
			.exchange()
			.expectStatus().isOk()
		// @formatter:on
	}

	/**
	 * This TestConfiguration is for ALL file testers, so do not delete this class.
	 *
	 * This is to configure the ObjectMapper with JSR310Module and Java 8 JavaTime()
	 * module that it is not initialized for test mode. i.e., ObjectMapper @Autowired does not exist
	 */
	@TestConfiguration
	class ObjectMapperConfiguration {
		@Bean
		fun objectMapper(): ObjectMapper = ObjectMapper().findAndRegisterModules()

	}
}


