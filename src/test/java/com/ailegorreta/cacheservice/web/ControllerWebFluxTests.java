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
package com.ailegorreta.cacheservice.web;

import com.ailegorreta.cacheservice.EnableTestContainers;
import com.ailegorreta.cacheservice.config.ResourceServerConfig;
import com.ailegorreta.cacheservice.config.ServiceConfig;
import com.ailegorreta.cacheservice.controller.CacheController;
import com.ailegorreta.cacheservice.model.DayType;
import com.ailegorreta.cacheservice.model.DocumentType;
import com.ailegorreta.cacheservice.model.SystemDate;
import com.ailegorreta.cacheservice.model.SystemRate;
import com.ailegorreta.cacheservice.repository.DocumentTypeRepository;
import com.ailegorreta.cacheservice.repository.SystemDateRepository;
import com.ailegorreta.cacheservice.repository.SystemRateRepository;
import com.ailegorreta.cacheservice.service.ParamService;
import com.ailegorreta.commons.utils.HasLogger;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * This class tests the REST calls received and y we do not have the cache in Redis database the the controller
 * via service will call param-service in order to fill the Redis database
 *
 * @proyect: cache-service
 * @author: rlh
 * @date: September 2023
 */
@WebFluxTest(CacheController.class)
@EnableTestContainers
@ExtendWith(MockitoExtension.class)
@Import({ServiceConfig.class, ResourceServerConfig.class, CacheController.class})
@ActiveProfiles("integration-tests-webflux")            // This is to permit duplicate singleton beans
class ControllerWebFluxTests implements HasLogger {

    @Autowired
    ApplicationContext applicationContext;
    @MockBean
    ParamService paramService;

    // @Autowired
    WebTestClient webTestClient;

    @MockBean
    private ReactiveJwtDecoder  reactiveJwtDecoder;
    /* ^ Mocks the Reactive JwtDecoder so that the application does not try to call Spring Security Server and get the
     * public keys for decoding the Access Token  */
    @MockBean
    private StreamBridge streamBridge;

    @Autowired
    DocumentTypeRepository documentTypeRepository;
    @Autowired
    SystemDateRepository systemDateRepository;
    @Autowired
    SystemRateRepository systemRateRepository;
    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext)
                                    // ^ add Spring Security test Support
                                    .apply(springSecurity())
                                    .configureClient()
                                    .build();
        // Must clean Redis database to avoid any future testing
        documentTypeRepository.deleteAll().block();
        StepVerifier.create(documentTypeRepository.count())
                    .expectNext(0L)
                    .verifyComplete();
        systemDateRepository.deleteAll().block();
        StepVerifier.create(systemDateRepository.count())
                .expectNext(0L)
                .verifyComplete();
        systemRateRepository.deleteAll().block();
        StepVerifier.create(systemRateRepository.count())
                .expectNext(0L)
                .verifyComplete();
    }

    /**
     * Test for System Rates (variables) in the system. Must be cached per variable.
     */
    @Test
    void whenGetAllSystemRatesAndAuthenticatedTheShouldReturn200() {
        var uri = UriComponentsBuilder.fromUriString("/cache/sysvar");
        var varName = "TIIF";

        when (paramService.getFromParamsSystemRate(varName)).thenReturn(
                new SystemRate("1", varName, BigDecimal.TEN, 0));

        getLogger().debug("Read 'TIIF' from the param service");
        var res = webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                               new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                               .get()
                               .uri(uri.queryParam("nombre", varName)
                                       .build().toUri())
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(BigDecimal.class)
                                .returnResult()
                                .getResponseBody();
        assertThat(res).isEqualTo(BigDecimal.TEN);

        getLogger().debug("Read 'TIIF' again but now from the cache");
        res = webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                           new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                            .get()
                            .uri(uri.build().toUri())
                            .exchange()
                            .expectStatus().isOk()
                            .expectBody(BigDecimal.class)
                            .returnResult()
                            .getResponseBody();
        assertThat(res).isEqualTo(BigDecimal.TEN);
    }

    /**
     * Test how to read systemDates with the CacheController
     */
    @Test
    void whenGetAllSystemDateAndAuthenticatedTheShouldReturn200() {
        var uri = UriComponentsBuilder.fromUriString("/cache/day");
        var today = LocalDate.of(2023, Month.SEPTEMBER, 10);       // note: September 10th is Sunday

        when (paramService.allSystemDates()).thenReturn(
                List.of(new SystemDate("1", DayType.HOY, today, 0),
                        new SystemDate("2", DayType.AYER, today.minusDays(1), 0),
                        new SystemDate("3", DayType.MANANA, today.now().plusDays(1), 0),
                        new SystemDate("4", DayType.FESTIVO, LocalDate.of(2023, Month.SEPTEMBER, 16), 0)));

        var res = webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                               new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                                                    .get()
                                                    .uri(uri.queryParam("days", 0)           // 0 => today
                                                            .build().toUri())
                                                    .exchange()
                                                    .expectStatus().isOk()
                                                    .expectBody(LocalDate.class);
        assertThat(res.returnResult().getResponseBody()).isEqualTo(LocalDate.of(2023, Month.SEPTEMBER, 8));
        // ^ Must be Friday because is not holiday

        getLogger().debug("Now read again 'today' with cache");
        res = webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                           new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                                                    .get()
                                                    .uri(uri.queryParam("days", 0)           // 0 => today
                                                            .build().toUri())
                                                    .exchange()
                                                    .expectStatus().isOk()
                                                    .expectBody(LocalDate.class);
        assertThat(res.returnResult().getResponseBody()).isEqualTo(LocalDate.of(2023, Month.SEPTEMBER, 8));

        getLogger().debug("Next invalidated cache");
        var uri2 = UriComponentsBuilder.fromUriString("/cache/invalid");
        webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                     new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                                                    .post()
                                                    .uri(uri2.build().toUri())
                                                    .exchange()
                                                    .expectStatus().isOk()
                                                    .expectBody(LocalDate.class);

        getLogger().debug("Now read again with the dates invalidated");
        res = webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                           new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                                                    .get()
                                                    .uri(uri.queryParam("days", 0)           // 0 => today
                                                            .build().toUri())
                                                    .exchange()
                                                    .expectStatus().isOk()
                                                    .expectBody(LocalDate.class);
        assertThat(res.returnResult().getResponseBody()).isEqualTo(LocalDate.of(2023, Month.SEPTEMBER, 8));
    }

    @Test
    void whenCheckNoHolidayAndAuthenticatedTheShouldReturn200() {
        var uri = UriComponentsBuilder.fromUriString("/cache/holiday");
        var today = LocalDate.of(2023, Month.SEPTEMBER, 12);
        var independenceDay = LocalDate.of(2023, Month.SEPTEMBER, 16);

        when(paramService.allSystemDates()).thenReturn(
                List.of(new SystemDate("1", DayType.HOY, today, 0),
                        new SystemDate("2", DayType.AYER, today.minusDays(1), 0),
                        new SystemDate("3", DayType.MANANA, today.now().plusDays(1), 0),
                        new SystemDate("4", DayType.FESTIVO, independenceDay, 0)));

        getLogger().debug("Check if today is holiday");
        var res = webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                               new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                                                .get()
                                                .uri(uri.queryParam("day", today)
                                                        .build().toUri())
                                                .exchange()
                                                .expectStatus().isOk()
                                                .expectBody(Boolean.class);
        assertThat(res.returnResult().getResponseBody()).isEqualTo(false);
    }

    @Test
    void whenCheckHolidayWeekendAndAuthenticatedTheShouldReturn200() {
        var uri = UriComponentsBuilder.fromUriString("/cache/holiday");
        var today = LocalDate.of(2023, Month.SEPTEMBER, 12);
        var independenceDay = LocalDate.of(2023, Month.SEPTEMBER, 16);
        var weekend = today.minusDays(2);

        when(paramService.allSystemDates()).thenReturn(
                List.of(new SystemDate("1", DayType.HOY, today, 0),
                        new SystemDate("2", DayType.AYER, today.minusDays(1), 0),
                        new SystemDate("3", DayType.MANANA, today.now().plusDays(1), 0),
                        new SystemDate("4", DayType.FESTIVO, independenceDay, 0)));

        getLogger().debug("Check a weekend" + weekend);
        var res = webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                               new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                                            .get()
                                            .uri(uri.queryParam("day", weekend)
                                                    .build().toUri())
                                            .exchange()
                                            .expectStatus().isOk()
                                            .expectBody(Boolean.class);
        assertThat(res.returnResult().getResponseBody()).isEqualTo(true);
    }

    @Test
    void whenCheckHolidayAndAuthenticatedTheShouldReturn200() {
        var uri = UriComponentsBuilder.fromUriString("/cache/holiday");
        var today = LocalDate.of(2023, Month.SEPTEMBER, 12);
        var independenceDay = LocalDate.of(2023, Month.SEPTEMBER, 16);

        when(paramService.allSystemDates()).thenReturn(
                List.of(new SystemDate("1", DayType.HOY, today, 0),
                        new SystemDate("2", DayType.AYER, today.minusDays(1), 0),
                        new SystemDate("3", DayType.MANANA, today.now().plusDays(1), 0),
                        new SystemDate("4", DayType.FESTIVO, independenceDay, 0)));

        getLogger().debug("And last check for independence day:" + independenceDay);
        var res = webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                               new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                                                    .get()
                                                    .uri(uri.queryParam("day", independenceDay)
                                                            .build().toUri())
                                                    .exchange()
                                                    .expectStatus().isOk()
                                                    .expectBody(Boolean.class);
        assertThat(res.returnResult().getResponseBody()).isEqualTo(true);
    }

    /**
     * Test how to read systemDocuments with the CacheController
     */
    @Test
    void whenReadAllDocumentsAndAuthenticatedTheShouldReturn200() throws Exception {
        var uri = UriComponentsBuilder.fromUriString("/cache/doctypes");
        var documents = List.of(new DocumentType("1", "Visa", "12m", 0),
                                new DocumentType("2", "Pasaporte", "12m", 0),
                                new DocumentType("3", "Comprobante domicilio", "3m", 0),
                                new DocumentType("4", "IFE", "24m", 0));
        when(paramService.allDocumentTypes()).thenReturn(documents);

        getLogger().debug("Check if documents catalog exist");
        StepVerifier.create(readDocumentTypes().getResponseBody())
                    .expectNext(Mono.just(documents))
                    .expectNextCount(4L)
                    .verifyComplete();
        Thread.sleep(5000);     // Wait that saveAll document in Redis has finished

        getLogger().debug("Now read 'again' them from cache");
        StepVerifier.create(readDocumentTypes().getResponseBody())
                .expectNext(Mono.just(documents))
                .expectNextCount(4L)
                .verifyComplete();
        Thread.sleep(5000);

        getLogger().debug("Now read 'again' withou StepVerifier");
        var res2 = webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                           new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                                                    .get()
                                                    .uri(uri.build().toUri())
                                                    .exchange()
                                                    .expectStatus().isOk()
                                                    .expectBody(List.class)
                                                    .returnResult()
                                                    .getResponseBody();
        assert res2 != null;
        assertThat(res2.size()).isEqualTo(4);
    }

    private FluxExchangeResult<Mono> readDocumentTypes() {
        var uri = UriComponentsBuilder.fromUriString("/cache/doctypes");

        return webTestClient.mutateWith(mockJwt().authorities(Arrays.asList(new SimpleGrantedAuthority("SCOPE_iam.facultad"),
                                                                            new SimpleGrantedAuthority("ROLE_ADMINLEGO"))))
                                                .get()
                                                .uri(uri.build().toUri())
                                                .exchange()
                                                .expectStatus().isOk()
                                                .returnResult(Mono.class);
    }

    @NotNull
    @Override
    public Logger getLogger() { return HasLogger.DefaultImpls.getLogger(this); }
}
