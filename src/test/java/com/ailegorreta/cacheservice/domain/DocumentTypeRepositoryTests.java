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
 *  DocumentTypeRepositoryTests.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.domain;

import com.ailegorreta.cacheservice.EnableTestContainers;
import com.ailegorreta.cacheservice.config.ServiceConfig;
import com.ailegorreta.cacheservice.model.DocumentType;
import com.ailegorreta.cacheservice.repository.DocumentTypeRepository;
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

import java.util.List;

/**
 * Class to just check that the Redis repositories are working correctly. No service or rest controller tests
 * are done in this class.
 *
 * Repository: DocumentTypeRepository
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
public class DocumentTypeRepositoryTests {

    @MockBean
    private StreamBridge streamBridge;
    @MockBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Qualifier("documentTypeRepositoryImpl")
    @Autowired
    DocumentTypeRepository repository;

    /**
     * Testing for DocumentTypeRepository
     */
    // Variable used to create a new entity before each test.
    private DocumentType savedDocumentType;

    /**
     * Before each test we save a DocumentType called "test"
     */
    @BeforeEach
    void setUpDB() {
        StepVerifier.create(repository.deleteAll()).verifyComplete();

        DocumentType documentType = new DocumentType("1", "test", "3m", 0);

        // Verify that we can save, store the created user into the savedDocument variable
        // and compare the saved user.
        StepVerifier.create(repository.save(documentType))
                    .expectNextMatches(createdDocument -> {
                        savedDocumentType = createdDocument;
                        return assertEqualDocumentType(documentType, savedDocumentType);
                    })
                    .verifyComplete();

        // Verify the number of entities in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    @Test
    void createTest() {
        DocumentType documentTypeA = new DocumentType(null, "test-A", "5m", 0);

        // Verify that we can save and compare the saved DocumentType
        StepVerifier.create(repository.save(documentTypeA))
                    .expectNextMatches(createdDocument ->
                        documentTypeA.getId() != null && createdDocument.getId().equals(documentTypeA.getId()))
                    .verifyComplete();

        // Verify we can get back the DocumentType by using findById method
        StepVerifier.create(repository.findById(documentTypeA.getId()))
                    .expectNextMatches(foundDocumentType -> assertEqualDocumentType(documentTypeA, foundDocumentType))
                    .verifyComplete();

        // Save without name and verify that it fails
        DocumentType documentTypeB = new DocumentType(null, "", "2m", 0);
        StepVerifier.create(repository.save(documentTypeB))
                    .expectError(IllegalArgumentException.class)
                    .verify();

        // Save without expiration and verify that it fails
        DocumentType documentTypeC = new DocumentType(null, "test-c", "", 0);
        StepVerifier.create(repository.save(documentTypeC))
                    .expectError(IllegalArgumentException.class)
                    .verify();

        // Verify that the database has only savedDocumentType & documentTypeA
        StepVerifier.create(repository.count())
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void saveAllTest() throws InterruptedException {
        var documents =  List.of(new DocumentType("2", "Pasaporte", "12m", 0),
                                 new DocumentType("3", "Comprobante domicilio", "3m", 0),
                                 new DocumentType("4", "IFE", "24m", 0));

        repository.saveAll(documents)
                  .doOnComplete(() -> System.out.println("Done"))
                  .subscribe();
        Thread.sleep(5000);

        // Verify that the database has the four document types saved
        StepVerifier.create(repository.count())
                    .expectNext(4L)
                    .verifyComplete();
    }

    @Test
    void updateTest() {
        String newName = "name-update";
        savedDocumentType.setName(newName);

        // Verify that we can update and compare the saved DocumentType new name
        StepVerifier.create(repository.save(savedDocumentType))
                    .expectNextMatches(updatedDocumentType -> updatedDocumentType.getId().equals(savedDocumentType.getId()) &&
                        updatedDocumentType.getName().equals(newName) && updatedDocumentType.getVersion() == 1)
                    .verifyComplete();

        // Verify that we can update and compare the saved DocumentType new expirationDate
        String newExpiration = "1Y";
        savedDocumentType.setExpiration(newExpiration);

        StepVerifier.create(repository.save(savedDocumentType))
                    .expectNextMatches(updatedDocumentType -> updatedDocumentType.getId().equals(savedDocumentType.getId()) &&
                        updatedDocumentType.getExpiration().equals(newExpiration) && updatedDocumentType.getVersion() == 2)
                    .verifyComplete();

        // Verify that we still have 1 entity in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    @Test
    void deleteTest() {
        // Verify that we can delete the saved DocumentType
        StepVerifier.create(repository.delete(savedDocumentType)).verifyComplete();

        // Verify that the DocumentType user has been deleted
        StepVerifier.create(repository.existsById(savedDocumentType.getId()))
                    .expectNext(false)
                    .verifyComplete();

        // This should also work since delete is an idempotent operation
        StepVerifier.create(repository.deleteById(savedDocumentType.getId())).verifyComplete();

        // Verify that we have no entity in the database
        StepVerifier.create(repository.count())
                    .expectNext(0L)
                    .verifyComplete();
    }

    @Test
    void getByNameTest() {
        // Verify that we can get the saved DocumentType by name
        StepVerifier.create(repository.findByName(savedDocumentType.getName()))
                    .expectNextMatches(foundDocumentType -> assertEqualDocumentType(savedDocumentType, foundDocumentType))
                    .verifyComplete();

        // Verify that we still have 1 entity in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    @Test
    void duplicateErrorTest() {
        // Same name will fail because name should be unique
        DocumentType documentType = new DocumentType(null, "test", "4m", 0);//using the same name as savedDocumentType

        // Verify that we have error due to duplicate name
        StepVerifier.create(repository.save(documentType))
                    .expectError(DuplicateKeyException.class)
                    .verify();

        // Add a new DocumentType and verify that it saves
        DocumentType newDocumentType = new DocumentType(null, "test-N", "3.45m", 0 );
        StepVerifier.create(repository.save(newDocumentType))
                    .expectNextMatches(createdDocumentType -> assertEqualDocumentType(newDocumentType, createdDocumentType))
                    .verifyComplete();

        // Verify that we only have 2 entities in the database
        StepVerifier.create(repository.count())
                    .expectNext(2L)
                    .verifyComplete();

        // Update savedDocumentType with the above test-N and verify duplicate error
        savedDocumentType.setName("test-N");
        StepVerifier.create(repository.save(savedDocumentType))
                    .expectError(DuplicateKeyException.class)
                    .verify();

        // Verify that name and version didn't change
        StepVerifier.create(repository.findById(savedDocumentType.getId()))
                    .expectNextMatches(foundDocumentType-> foundDocumentType.getName().equals("test")
                                        && foundDocumentType.getVersion() == 0)
                    .verifyComplete();
    }

    @Test
    void optimisticLockErrorTest() {
        // Store the saved DocumentType in two separate objects
        DocumentType documentType1 = repository.findById(savedDocumentType.getId()).block(); // Wait by blocking the thread
        DocumentType documentType2 = repository.findById(savedDocumentType.getId()).block(); // Wait by blocking the thread

        Assertions.assertNotNull(documentType1); // Assert it is not null
        Assertions.assertNotNull(documentType2); // Assert it is not null
        Assertions.assertEquals(documentType1.getVersion(), documentType2.getVersion()); // Assert both version are same
        assertEqualDocumentType(documentType1, documentType2);

        String newName1 = "New Name Object1";
        String newName2 = "New Name Object2";

        // Update the DocumentType using the first user object. THIS WILL WORK
        documentType1.setName(newName1);
        StepVerifier.create(repository.save(documentType1))
                    .expectNextMatches(updatedDocumentType -> updatedDocumentType.getVersion() == 1)
                    .verifyComplete();

        // Update the documentType using the second object.
        // This should FAIL since this second object now holds an old version number, i.e. an Optimistic Lock
        documentType2.setName(newName2);
        StepVerifier.create(repository.save(documentType2))
                    .expectError(OptimisticLockingFailureException.class)
                    .verify();

        // Get the updated DocumentType from the database and verify its new state
        StepVerifier.create(repository.findById(savedDocumentType.getId()))
                    .expectNextMatches(foundDocumentType ->
                        foundDocumentType.getVersion() == 1 &&
                                foundDocumentType.getName().equals(newName1))
                    .verifyComplete();

        // Verify we still have one DocumentType in the database
        StepVerifier.create(repository.count())
                    .expectNext(1L)
                    .verifyComplete();
    }

    // Personal method used in the tests above to compare the DocumentType entity.
    private boolean assertEqualDocumentType(DocumentType expectedDocumentType, DocumentType actualDocumentType) {
        Assertions.assertEquals(expectedDocumentType.getId(), actualDocumentType.getId());
        Assertions.assertEquals(expectedDocumentType.getName(), actualDocumentType.getName());
        Assertions.assertEquals(expectedDocumentType.getExpiration(), actualDocumentType.getExpiration());

        return (expectedDocumentType.getId().equals(actualDocumentType.getId())) &&
                (expectedDocumentType.getExpiration().equals(actualDocumentType.getExpiration())) &&
                (expectedDocumentType.getName().equals(actualDocumentType.getName())) &&
                (expectedDocumentType.getVersion() == actualDocumentType.getVersion());
    }

}
