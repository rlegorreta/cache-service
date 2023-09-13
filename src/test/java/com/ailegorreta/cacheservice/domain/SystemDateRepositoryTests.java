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
 *  SystemDateRepositoryTests.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.domain;

import com.ailegorreta.cacheservice.EnableTestContainers;
import com.ailegorreta.cacheservice.config.ServiceConfig;
import com.ailegorreta.cacheservice.model.DayType;
import com.ailegorreta.cacheservice.model.SystemDate;
import com.ailegorreta.cacheservice.repository.SystemDateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

/**
 * Class to just check that the Redis repositories are working correctly. No service or rest controller tests
 * are done in this class.
 *
 * Repository: SystemDate
 *
 * @project cache-service
 * @author rlh
 * @date September 2023
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers
/* ^ This is a custom annotation to load the containers */
@Import(ServiceConfig.class)
@ActiveProfiles("integration-tests")
@DirtiesContext
public class SystemDateRepositoryTests {

    @MockBean
    private StreamBridge streamBridge;
    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Qualifier("systemDateRepositoryImpl")
    @Autowired
    SystemDateRepository repository;

    /**
     * Testing for SystemDateRepository
     */
    // Variable used to create a new entity before each test.
    private SystemDate savedSystemDate;

    /**
     * Before each test we save a SystemDate called "test" as a Today
     */
    @BeforeEach
    void setUpDB() {
        StepVerifier.create(repository.deleteAll()).verifyComplete();

        SystemDate today = new SystemDate("1", DayType.HOY, LocalDate.now(), 0);

        // Verify that we can save, store the created user into the savedSystemDate variable
        // and compare the saved user.
        StepVerifier.create(repository.save(today))
                    .expectNextMatches(createdToday -> {
                        savedSystemDate = createdToday;
                        return assertEqualSystemDate(createdToday, savedSystemDate);
                    })
                    .verifyComplete();

        // Verify the number of entities in the database
        StepVerifier.create(repository.count())
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    void createTest() {
        SystemDate systemDateA = new SystemDate(null, DayType.MANANA, LocalDate.now().plusDays(1), 0);

        // Verify that we can save and compare the saved SystemDate
        StepVerifier.create(repository.save(systemDateA))
                    .expectNextMatches(createdSystemDate ->
                            systemDateA.getId() != null && createdSystemDate.getId().equals(systemDateA.getId()))
                    .verifyComplete();

        // Verify we can get back the SystemDate by using findById method
        StepVerifier.create(repository.findById(systemDateA.getId()))
                    .expectNextMatches(foundSystemDate -> assertEqualSystemDate(systemDateA, foundSystemDate))
                    .verifyComplete();

        // DateType and Day are checked by Kotlin so we don´t validate them

        // Verify that the database has only systemDateA & systemDateB
        StepVerifier.create(repository.count())
                    .expectNext(2L)
                    .verifyComplete();
    }

    @Test
    void saveAllTest() throws InterruptedException {
        var today = LocalDate.now();
        var days =  List.of(new SystemDate("2", DayType.AYER, today.minusDays(1), 0),
                            new SystemDate("3", DayType.MANANA, today.now().plusDays(1), 0),
                            new SystemDate("4", DayType.FESTIVO, LocalDate.of(2023, Month.SEPTEMBER, 16), 0));

        repository.saveAll(days)
                  .doOnComplete(() -> System.out.println("Done"))
                  .subscribe();

        Thread.sleep(5000);
        // Verify that the database has the four system dates saved
        StepVerifier.create(repository.count())
                    .expectNext(4L)
                    .verifyComplete();
    }

    @Test
    void updateTest() {
        DayType newDateType = DayType.AYER;
        savedSystemDate.setName(newDateType);

        // Verify that we can update and compare the saved SystemDate new DateType
        StepVerifier.create(repository.save(savedSystemDate))
                    .expectNextMatches(updatedSystemDate -> updatedSystemDate.getId().equals(savedSystemDate.getId()) &&
                            updatedSystemDate.getName().equals(newDateType) && updatedSystemDate.getVersion() == 1)
                    .verifyComplete();

        // Verify that we can update and compare the saved SystemDate new Day
        LocalDate pastDay = LocalDate.now().minusDays(1);
        savedSystemDate.setDay(pastDay);

        StepVerifier.create(repository.save(savedSystemDate))
                    .expectNextMatches(updatedSystemDate -> updatedSystemDate.getId().equals(savedSystemDate.getId()) &&
                            updatedSystemDate.getDay().equals(pastDay) && updatedSystemDate.getVersion() == 2)
                    .verifyComplete();

        // Verify that we still have 1 entity in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    @Test
    void deleteTest() {
        // Verify that we can delete the saved SystemDate (today)
        StepVerifier.create(repository.delete(savedSystemDate)).verifyComplete();

        // Verify that the SystemDate user has been deleted
        StepVerifier.create(repository.existsById(savedSystemDate.getId()))
                    .expectNext(false)
                    .verifyComplete();

        // This should also work since delete is an idempotent operation
        StepVerifier.create(repository.deleteById(savedSystemDate.getId())).verifyComplete();

        // Verify that we have no entity in the database
        StepVerifier.create(repository.count())
                    .expectNext(0L)
                    .verifyComplete();
    }

    @Test
    void getByNameTest() {
        // Verify that we can get the saved SystemDate by DayType
        StepVerifier.create(repository.findByName(savedSystemDate.getName()))
                    .expectNextMatches(foundSystemDate -> assertEqualSystemDate(savedSystemDate, foundSystemDate))
                    .verifyComplete();

        // Verify that we still have 1 entity in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    @Test
    void duplicateErrorTest() {
        // Same name will fail because today should be unique
        SystemDate today = new SystemDate(null, DayType.HOY, LocalDate.now(), 0);

        // Verify that we have error due to duplicate name
        StepVerifier.create(repository.save(today))
                    .expectError(DuplicateKeyException.class)
                    .verify();

        // Add a new SystemDate as holiday and verify that it saves
        SystemDate newHoliday = new SystemDate(null, DayType.FESTIVO, LocalDate.now(), 0 );
        StepVerifier.create(repository.save(newHoliday))
                    .expectNextMatches(createdSystemDate -> assertEqualSystemDate(newHoliday, createdSystemDate))
                    .verifyComplete();

        // Add a new second SystemDate as holiday and verify that it saves. Holidays are not unique
        SystemDate newHolidayB = new SystemDate(null, DayType.FESTIVO, LocalDate.now().plusDays(10), 0 );
        StepVerifier.create(repository.save(newHolidayB))
                    .expectNextMatches(createdSystemDate -> assertEqualSystemDate(newHolidayB, createdSystemDate))
                    .verifyComplete();

        // Add a yesterday SystemDate and verify that it saves.
        SystemDate yesterday = new SystemDate(null, DayType.AYER, LocalDate.now().minusDays(1), 0 );
        StepVerifier.create(repository.save(yesterday))
                    .expectNextMatches(createdSystemDate -> assertEqualSystemDate(yesterday, createdSystemDate))
                    .verifyComplete();

        // Verify that we only have 4 entities in the database
        StepVerifier.create(repository.count())
                    .expectNext(4L)
                    .verifyComplete();

        // Update savedDocumentType with the above yesterday and verify duplicate error
        savedSystemDate.setName(DayType.AYER);
        StepVerifier.create(repository.save(savedSystemDate))
                    .expectError(DuplicateKeyException.class)
                    .verify();

        // Verify that name and version didn't change
        StepVerifier.create(repository.findById(savedSystemDate.getId()))
                    .expectNextMatches(foundSystemDate -> foundSystemDate.getName().equals(DayType.HOY)
                                        && foundSystemDate.getVersion() == 0)
                    .verifyComplete();
    }

    @Test
    void optimisticLockErrorTest() {
        // Store the saved DocumentType in two separate objects
        SystemDate systemDate1 = repository.findById(savedSystemDate.getId()).block(); // Wait by blocking the thread
        SystemDate systemDate2 = repository.findById(savedSystemDate.getId()).block(); // Wait by blocking the thread

        Assertions.assertNotNull(systemDate1); // Assert it is not null
        Assertions.assertNotNull(systemDate2); // Assert it is not null
        Assertions.assertEquals(systemDate1.getVersion(), systemDate2.getVersion()); // Assert both version are same
        assertEqualSystemDate(systemDate1, systemDate2);

        LocalDate newDate1 = LocalDate.now().plusDays(11);
        LocalDate newDate2 = LocalDate.now().plusDays(12);

        // Update the systemDate using the first user object. THIS WILL WORK
        systemDate1.setDay(newDate1);
        StepVerifier.create(repository.save(systemDate1))
                    .expectNextMatches(updatedSystemDate -> updatedSystemDate.getVersion() == 1)
                    .verifyComplete();

        // Update the systemDate using the second object.
        // This should FAIL since this second object now holds an old version number, i.e. an Optimistic Lock
        systemDate2.setDay(newDate2);
        StepVerifier.create(repository.save(systemDate2))
                    .expectError(OptimisticLockingFailureException.class)
                    .verify();

        // Get the updated systemDate from the database and verify its new state
        StepVerifier.create(repository.findById(savedSystemDate.getId()))
                    .expectNextMatches(foundSystemDate -> foundSystemDate.getVersion() == 1 &&
                                        foundSystemDate.getDay().equals(newDate1))
                    .verifyComplete();

        // Verify we still have one systemDate in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    // Personal method used in the tests above to compare the SystemDate entity.
    private boolean assertEqualSystemDate(SystemDate expectedSystemDate, SystemDate actualSystemDate) {
        Assertions.assertEquals(expectedSystemDate.getId(), actualSystemDate.getId());
        Assertions.assertEquals(expectedSystemDate.getName(), actualSystemDate.getName());
        Assertions.assertEquals(expectedSystemDate.getDay(), actualSystemDate.getDay());

        return (expectedSystemDate.getId().equals(actualSystemDate.getId())) &&
                (expectedSystemDate.getDay().equals(actualSystemDate.getDay())) &&
                (expectedSystemDate.getName().equals(actualSystemDate.getName())) &&
                (expectedSystemDate.getVersion() == actualSystemDate.getVersion());
    }

}
