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
 *  SystemRateRepositoryTests.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.domain;

import com.ailegorreta.cacheservice.EnableTestContainers;
import com.ailegorreta.cacheservice.config.ServiceConfig;
import com.ailegorreta.cacheservice.model.SystemRate;
import com.ailegorreta.cacheservice.repository.SystemRateRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;

/**
 * Class to just check that the Redis repositories are working correctly. No service or rest controller tests
 * are done in this class.
 *
 * Repository: SystemRateRepository
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
public class SystemRateRepositoryTests {

    @MockBean
    private StreamBridge streamBridge;
    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Qualifier("systemRateRepositoryImpl")
    @Autowired
    SystemRateRepository repository;

    /**
     * Testing for SystemRateRepository
     */
    // Variable used to create a new entity before each test.
    private SystemRate savedSystemRate;

    /**
     * Before each test we save a SystemRate called "test"
     */
    @BeforeEach
    void setUpDB() {
        StepVerifier.create(repository.deleteAll()).verifyComplete();

        SystemRate systemRate = new SystemRate(null, "test", BigDecimal.ONE, 0);

        // Verify that we can save, store the created user into the savedSystemRate variable
        // and compare the saved user.
        StepVerifier.create(repository.save(systemRate))
                    .expectNextMatches(createdRate -> {
                        savedSystemRate = createdRate;
                        return assertEqualSystemRate(systemRate, savedSystemRate);
                    })
                    .verifyComplete();

        // Verify the number of entities in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    @Test
    void createTest() {
        SystemRate systemRateA = new SystemRate(null, "test-A", BigDecimal.ONE, 0);

        // Verify that we can save and compare the saved user
        StepVerifier.create(repository.save(systemRateA))
                .expectNextMatches(createdDocument ->
                        systemRateA.getId() != null && createdDocument.getId().equals(systemRateA.getId()))
                .verifyComplete();

        // Verify we can get back the SystemRate by using findById method
        StepVerifier.create(repository.findById(systemRateA.getId()))
                .expectNextMatches(foundSystemRate -> assertEqualSystemRate(systemRateA, foundSystemRate))
                .verifyComplete();

        // Save without name and verify that it fails
        SystemRate systemRateB = new SystemRate(null, "", BigDecimal.ONE, 0);
        StepVerifier.create(repository.save(systemRateB))
                    .expectError(IllegalArgumentException.class)
                    .verify();

        // Save without rate and verify that it fails
        SystemRate systemRateC = new SystemRate(null, "test-c", BigDecimal.ZERO, 0);
        StepVerifier.create(repository.save(systemRateC))
                    .expectError(IllegalArgumentException.class)
                    .verify();

        // Verify that the database has only savedSystemRate & systemRateA
        StepVerifier.create(repository.count())
                    .expectNext(2L)
                    .verifyComplete();
    }

    @Test
    void updateTest() {
        String newName = "name-update";
        savedSystemRate.setName(newName);

        // Verify that we can update and compare the saved systemRate new name
        StepVerifier.create(repository.save(savedSystemRate))
                    .expectNextMatches(updatedSystemRate -> updatedSystemRate.getId().equals(savedSystemRate.getId()) &&
                            updatedSystemRate.getName().equals(newName) && updatedSystemRate.getVersion() == 1)
                    .verifyComplete();

        // Verify that we can update and compare the saved systemRate new rate
        BigDecimal newRate = BigDecimal.TEN;
        savedSystemRate.setRate(newRate);

        StepVerifier.create(repository.save(savedSystemRate))
                    .expectNextMatches(updatedSystemRate -> updatedSystemRate.getId().equals(savedSystemRate.getId()) &&
                            updatedSystemRate.getRate().equals(newRate) && updatedSystemRate.getVersion() == 2)
                    .verifyComplete();

        // Verify that we still have 1 entity in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    @Test
    void deleteTest() {
        // Verify that we can delete the saved SystemRate
        StepVerifier.create(repository.delete(savedSystemRate)).verifyComplete();

        // Verify that the saved SystemRate has been deleted
        StepVerifier.create(repository.existsById(savedSystemRate.getId()))
                    .expectNext(false)
                    .verifyComplete();

        // This should also work since delete is an idempotent operation
        StepVerifier.create(repository.deleteById(savedSystemRate.getId())).verifyComplete();

        // Verify that we have no entity in the database
        StepVerifier.create(repository.count())
                    .expectNext(0L)
                    .verifyComplete();
    }

    @Test
    void getByNameTest() {
        // Verify that we can get the saved SystemDate by name
        StepVerifier.create(repository.findByName(savedSystemRate.getName()))
                    .expectNextMatches(foundSystemRate -> assertEqualSystemRate(savedSystemRate, foundSystemRate))
                    .verifyComplete();

        // Verify that we still have 1 entity in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    @Test
    void duplicateErrorTest() {
        // Same name will fail because name should be unique
        SystemRate systemRate = new SystemRate(null, "test", BigDecimal.ONE, 0);//using the same name as savedSystemRate

        // Verify that we have error due to duplicate username
        StepVerifier.create(repository.save(systemRate))
                    .expectError(DuplicateKeyException.class)
                    .verify();

        // Add a new SystemRate and verify that it saves
        SystemRate newSystemRate = new SystemRate(null, "test-N", BigDecimal.ONE, 0 );
        StepVerifier.create(repository.save(newSystemRate))
                    .expectNextMatches(createdSystemRate -> assertEqualSystemRate(newSystemRate, createdSystemRate))
                    .verifyComplete();

        // Verify that we only have 2 entities in the database
        StepVerifier.create(repository.count())
                    .expectNext(2L)
                    .verifyComplete();

        // Update savedSystemRate with the above test-N and verify duplicate error
        savedSystemRate.setName("test-N");
        StepVerifier.create(repository.save(savedSystemRate))
                    .expectError(DuplicateKeyException.class)
                    .verify();

        // Verify that name and version didn't change
        StepVerifier.create(repository.findById(savedSystemRate.getId()))
                    .expectNextMatches(foundSystemRate -> foundSystemRate.getName().equals("test")
                                        && foundSystemRate.getVersion() == 0)
                    .verifyComplete();
    }

    @Test
    void optimisticLockErrorTest() {
        // Store the saved SystemRate in two separate objects
        SystemRate systemRate1 = repository.findById(savedSystemRate.getId()).block(); // Wait by blocking the thread
        SystemRate systemRate2 = repository.findById(savedSystemRate.getId()).block(); // Wait by blocking the thread

        Assertions.assertNotNull(systemRate1); // Assert it is not null
        Assertions.assertNotNull(systemRate2); // Assert it is not null
        Assertions.assertEquals(systemRate1.getVersion(), systemRate2.getVersion()); // Assert both version are same
        assertEqualSystemRate(systemRate1, systemRate2);

        String newName1 = "New Name Object1";
        String newName2 = "New Name Object2";

        // Update the SystemRate using the first user object. THIS WILL WORK
        systemRate1.setName(newName1);
        StepVerifier.create(repository.save(systemRate1))
                    .expectNextMatches(updatedSytemRate-> updatedSytemRate.getVersion() == 1)
                    .verifyComplete();

        // Update the documentType using the second object.
        // This should FAIL since this second object now holds an old version number, i.e. an Optimistic Lock
        systemRate2.setName(newName2);
        StepVerifier.create(repository.save(systemRate2))
                    .expectError(OptimisticLockingFailureException.class)
                    .verify();

        // Get the updated SystemRate from the database and verify its new state
        StepVerifier.create(repository.findById(savedSystemRate.getId()))
                    .expectNextMatches(foundSystemRate ->
                            foundSystemRate.getVersion() == 1 &&
                            foundSystemRate.getName().equals(newName1))
                    .verifyComplete();

        // Verify we still have one SystemRate in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    // Personal method used in the tests above to compare the SystemRate entity.
    private boolean assertEqualSystemRate(SystemRate expectedSystemRate, SystemRate actualSystemRate) {
        Assertions.assertEquals(expectedSystemRate.getId(), actualSystemRate.getId());
        Assertions.assertEquals(expectedSystemRate.getName(), actualSystemRate.getName());
        Assertions.assertEquals(expectedSystemRate.getRate(), actualSystemRate.getRate());

        return (expectedSystemRate.getId().equals(actualSystemRate.getId())) &&
                (expectedSystemRate.getRate().equals(actualSystemRate.getRate())) &&
                (expectedSystemRate.getName().equals(actualSystemRate.getName())) &&
                (expectedSystemRate.getVersion() == actualSystemRate.getVersion());
    }

}
