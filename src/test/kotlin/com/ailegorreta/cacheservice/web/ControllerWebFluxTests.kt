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
 *  ControllerWebFluxTests.kt
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.web

import com.ailegorreta.cacheservice.EnableTestContainers
import com.ailegorreta.cacheservice.config.ResourceServerConfig
import com.ailegorreta.cacheservice.config.ServiceConfig
import com.ailegorreta.cacheservice.controller.CacheController
import com.ailegorreta.cacheservice.model.DayType
import com.ailegorreta.cacheservice.model.DocumentType
import com.ailegorreta.cacheservice.model.SystemDate
import com.ailegorreta.cacheservice.model.SystemRate
import com.ailegorreta.cacheservice.repository.DocumentTypeRepository
import com.ailegorreta.cacheservice.repository.SystemDateRepository
import com.ailegorreta.cacheservice.repository.SystemRateRepository
import com.ailegorreta.cacheservice.service.ParamService
import com.ailegorreta.commons.utils.HasLogger
import io.mockk.every
import io.mockk.mockkObject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.util.*


/**
 * This class tests the REST calls received, we do not have the cache in Redis database the controller
 * via repository will call cache-service in order to fill the Redis database
 *
 * note: This class must be in Kotlin because we use the Coroutines in the repositories
 *
 * note: The Mockito needs a testing library to work properly in Kotlin. For more information see:
 * https://www.baeldung.com/kotlin/mockk#:~:text=In%20Kotlin%2C%20all%20classes%20and,that%20we%20want%20to%20mock.
 *
 * @proyect: cache-service
 * @author: rlh
 * @date: September 2023
 */
@WebFluxTest(CacheController::class)
@EnableTestContainers
@ExtendWith(MockitoExtension::class)
@Import(ServiceConfig::class, ResourceServerConfig::class, CacheController::class)
@ActiveProfiles("integration-tests-webflux")
internal class ControllerWebFluxTests : HasLogger {
    @Autowired
    var applicationContext: ApplicationContext? = null

    @Autowired
    private val paramService: ParamService? = null

    // @Autowired
    private var webTestClient: WebTestClient? = null

    @MockBean
    private val reactiveJwtDecoder: ReactiveJwtDecoder? = null
    /* ^ Mocks the Reactive JwtDecoder so that the application does not try to call Spring Security Server and get the
     * public keys for decoding the Access Token  */
    @MockBean
    private val streamBridge: StreamBridge? = null

    @Autowired
    var documentTypeRepository: DocumentTypeRepository? = null

    @Autowired
    var systemDateRepository: SystemDateRepository? = null

    @Autowired
    var systemRateRepository: SystemRateRepository? = null
    @BeforeEach
    fun setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext!!)
                                      // ^ add Spring Security test Support
                                    .apply(SecurityMockServerConfigurers.springSecurity())
                                    .configureClient()
                                    .build()
        // Must clean Redis database to avoid any future testing. Could use coroutines
        documentTypeRepository!!.deleteAll().block()
        StepVerifier.create(documentTypeRepository!!.count())
                    .expectNext(0L)
                    .verifyComplete()
        systemDateRepository!!.deleteAll().block()
        StepVerifier.create(systemDateRepository!!.count())
                    .expectNext(0L)
                    .verifyComplete()
        systemRateRepository!!.deleteAll().block()
        StepVerifier.create(systemRateRepository!!.count())
                    .expectNext(0L)
                    .verifyComplete()
    }

    /**
     * Test for System Rates (variables) in the system. Must be cached per variable.
     *
     * note: The SystemRateRepository does NOT utilize coroutines because we need it is not
     *      necessary and just with the Redis reactive Redis is necessary. So the logger
     *      debugger messages go in different order.
     */
    @Test
    fun `Read system variables and check if they exist or not in cache`() {
        val uri = UriComponentsBuilder.fromUriString("/cache/sysvar")
        val varName = "TIIF"

        mockkObject(paramService!!)
        every { runBlocking { paramService.getFromParamsSystemRate(varName) }} returns SystemRate("1", varName, BigDecimal.TEN, 0)

        logger.debug("Read 'TIIF' from the param service")
        var res = webTestClient!!.mutateWith(SecurityMockServerConfigurers.mockJwt().authorities(
                                listOf<GrantedAuthority>(
                                        SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                        SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                )))
                                .get()
                                .uri(
                                    uri.queryParam("nombre", varName)
                                        .build().toUri()
                                )
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(BigDecimal::class.java)
                                .returnResult()
                                .responseBody
        assertThat(res).isEqualTo(BigDecimal.TEN)
        Thread.sleep(3000) // Wait that save the variable in Redis
        logger.debug("Read 'TIIF' again but now from the cache")
        res = webTestClient!!.mutateWith( SecurityMockServerConfigurers.mockJwt().authorities(
                                listOf<GrantedAuthority>(
                                    SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                    SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                )))
                                .get()
                                .uri(uri.build().toUri())
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(BigDecimal::class.java)
                                .returnResult()
                                .responseBody
        assertThat(res).isEqualTo(BigDecimal.TEN)
    }

    /**
     * Test how to read systemDates with the CacheController
     */
    @FlowPreview
    @Test
    fun `Read all system dates and invalidate cache`(): Unit = runBlocking {
        val uri = UriComponentsBuilder.fromUriString("/cache/day")
        val today = LocalDate.of(2023, Month.SEPTEMBER, 10) // note: September 10th is Sunday
        val independenceDay = LocalDate.of(2023, Month.SEPTEMBER, 16)
        val dates = listOf(
                            SystemDate("1", DayType.HOY, today, 0),
                            SystemDate("2", DayType.AYER, today.minusDays(1), 0),
                            SystemDate("3", DayType.MANANA, LocalDate.now().plusDays(1), 0),
                            SystemDate("4", DayType.FESTIVO, independenceDay, 0)
                            )

        mockkObject(paramService!!)
        every { runBlocking { paramService!!.allSystemDates() }} returns dates

        var res = webTestClient!!.mutateWith(SecurityMockServerConfigurers.mockJwt().authorities(
                                            listOf<GrantedAuthority>(
                                                SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                 )))
                                 .get()
                                 .uri(uri.queryParam("days", 0) // 0 => today
                                         .build().toUri()
                                 )
                                 .exchange()
                                 .expectStatus().isOk()
                                 .expectBody(LocalDate::class.java)

        assertThat(res.returnResult().responseBody).isEqualTo(LocalDate.of(2023, Month.SEPTEMBER, 8))
        // ^ Must be Friday because is not holiday
        logger.debug("Now read again 'today' with cache")
        res = webTestClient!!.mutateWith(SecurityMockServerConfigurers.mockJwt().authorities(
                                            listOf<GrantedAuthority>(
                                                SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                )))
                                .get()
                                .uri(uri.queryParam("days", 0) // 0 => today
                                        .build().toUri()
                                )
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(LocalDate::class.java)

        assertThat(res.returnResult().responseBody).isEqualTo(LocalDate.of(2023, Month.SEPTEMBER, 8))

        logger.debug("Next invalidated cache")
        val uri2 = UriComponentsBuilder.fromUriString("/cache/invalid")
        webTestClient!!.mutateWith(SecurityMockServerConfigurers.mockJwt().authorities(
                                    listOf<GrantedAuthority>(
                                        SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                        SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                )))
                                .post()
                                .uri(uri2.build().toUri())
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(LocalDate::class.java)

        logger.debug("Now read again with the dates invalidated")
        res = webTestClient!!.mutateWith(SecurityMockServerConfigurers.mockJwt().authorities(
                                    listOf<GrantedAuthority>(
                                            SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                            SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                )))
                                .get()
                                .uri(uri.queryParam("days", 0) // 0 => today
                                        .build().toUri()
                                )
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(LocalDate::class.java)

        assertThat(res.returnResult().responseBody).isEqualTo(LocalDate.of(2023, Month.SEPTEMBER, 8))
    }

    @Test
    fun `check day is normal day no holiday`() {
        val uri = UriComponentsBuilder.fromUriString("/cache/holiday")
        val today = LocalDate.of(2023, Month.SEPTEMBER, 12)
        val independenceDay = LocalDate.of(2023, Month.SEPTEMBER, 16)
        val dates = listOf(
                        SystemDate("1", DayType.HOY, today, 0),
                        SystemDate("2", DayType.AYER, today.minusDays(1), 0),
                        SystemDate("3", DayType.MANANA, today.plusDays(1), 0),
                        SystemDate("4", DayType.FESTIVO, independenceDay, 0)
                    )
        mockkObject(paramService!!)
        every { runBlocking { paramService!!.allSystemDates() }} returns dates

        logger.debug("Check if today is holiday")
        val res = webTestClient!!.mutateWith(SecurityMockServerConfigurers.mockJwt().authorities(
                                            listOf<GrantedAuthority>(
                                                SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                            )))
                                    .get()
                                    .uri(
                                        uri.queryParam("day", today)
                                            .build().toUri()
                                    )
                                    .exchange()
                                    .expectStatus().isOk()
                                    .expectBody(Boolean::class.java)
        assertThat(res.returnResult().responseBody).isEqualTo(false)
    }

    @Test
    fun `check day is holiday`() {
        val uri = UriComponentsBuilder.fromUriString("/cache/holiday")
        val today = LocalDate.of(2023, Month.SEPTEMBER, 12)
        val independenceDay = LocalDate.of(2023, Month.SEPTEMBER, 16)
        val weekend = today.minusDays(2)
        val dates = listOf(
                        SystemDate("1", DayType.HOY, today, 0),
                        SystemDate("2", DayType.AYER, today.minusDays(1), 0),
                        SystemDate("3", DayType.MANANA, today.plusDays(1), 0),
                        SystemDate("4", DayType.FESTIVO, independenceDay, 0)
                    )

        mockkObject(paramService!!)
        every { runBlocking { paramService!!.allSystemDates() }} returns dates

        logger.debug("Check a weekend$weekend")
        val res = webTestClient!!.mutateWith(SecurityMockServerConfigurers.mockJwt().authorities(
                                    listOf<GrantedAuthority>(
                                            SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                            SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                    )))
                                    .get()
                                    .uri(
                                        uri.queryParam("day", weekend)
                                            .build().toUri()
                                    )
                                    .exchange()
                                    .expectStatus().isOk()
                                    .expectBody(Boolean::class.java)
        assertThat(res.returnResult().responseBody).isEqualTo(true)
    }

    @Test
    fun `check for independence day`() {
        val uri = UriComponentsBuilder.fromUriString("/cache/holiday")
        val today = LocalDate.of(2023, Month.SEPTEMBER, 12)
        val independenceDay = LocalDate.of(2023, Month.SEPTEMBER, 16)
        val dates = listOf(
                        SystemDate("1", DayType.HOY, today, 0),
                        SystemDate("2", DayType.AYER, today.minusDays(1), 0),
                        SystemDate("3", DayType.MANANA, today.plusDays(1), 0),
                        SystemDate("4", DayType.FESTIVO, independenceDay, 0)
                    )

        mockkObject(paramService!!)
        every { runBlocking { paramService!!.allSystemDates() }} returns dates;

        logger.debug("And last check for independence day:$independenceDay")
        val res = webTestClient!!.mutateWith(SecurityMockServerConfigurers.mockJwt().authorities(
                                                listOf<GrantedAuthority>(
                                                    SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                    SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                            )))
                                .get()
                                .uri(
                                    uri.queryParam("day", independenceDay)
                                        .build().toUri()
                                )
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(Boolean::class.java)
        assertThat(res.returnResult().responseBody).isEqualTo(true)
    }

    /**
     * Test how to read systemDocuments with the CacheController
     */
    @Test
    @Throws(Exception::class)
    fun `validate document type catalog and cache`() {
        val uri = UriComponentsBuilder.fromUriString("/cache/doctypes")
        val documents = arrayOf(
                            DocumentType("1", "Visa", "12m", 0),
                            DocumentType("2", "Pasaporte", "12m", 0),
                            DocumentType("3", "Comprobante domicilio", "3m", 0),
                            DocumentType("4", "IFE", "24m", 0)
                         )

        mockkObject(paramService!!)
        every { runBlocking { paramService.allDocumentTypes() }} returns documents.asList()

        logger.debug("Check if documents catalog exist")
        assertThat(readDocumentTypes().responseBody!!.size).isEqualTo(documents.size)
        // ^ They came in different sort order so we just check size
        Thread.sleep(3000) // Wait that saveAll document in Redis has finished

        logger.debug("Now read 'again' them from cache")
        assertThat(readDocumentTypes().responseBody!!.size).isEqualTo(documents.size)
    }

    private fun readDocumentTypes(): EntityExchangeResult<Array<DocumentType>> {
        val uri = UriComponentsBuilder.fromUriString("/cache/doctypes")

        return webTestClient!!.mutateWith(SecurityMockServerConfigurers.mockJwt().authorities(
                                    listOf<GrantedAuthority>(
                                        SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                        SimpleGrantedAuthority("ROLE_ADMINLEGO")
                                    )))
                            .get()
                            .uri(uri.build().toUri())
                            .exchange()
                            .expectStatus().isOk()
                            .expectBody(Array<DocumentType>::class.java)
                            .returnResult()
    }

}
