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
 *  DocumentTypeRepositoryImpl.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.repository.impl;

import com.ailegorreta.cacheservice.model.DocumentType;
import com.ailegorreta.cacheservice.repository.DocumentTypeRepository;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementation for the CRUD reactive repository for DocumentType
 *
 * @project: cache-service
 * @author rlh
 * @date: September 2023
 */
@Repository("documentTypeRepositoryImpl")
public class DocumentTypeRepositoryImpl implements DocumentTypeRepository {

    private final static String KEY = "DOCUMENT_TYPE";
    private final static String REDIS_PREFIX = "_R";
    private final ReactiveRedisOperations<String, DocumentType> redisOperations;
    private final ReactiveHashOperations<String, String, DocumentType> hashOperations;

    @Autowired
    public DocumentTypeRepositoryImpl(ReactiveRedisOperations<String, DocumentType> redisOperations) {
        this.redisOperations = redisOperations;
        this.hashOperations = redisOperations.opsForHash();
    }

    @Override
    public Mono<DocumentType> findById(String id) {
        return hashOperations.get(KEY, id);
    }

    @Override
    public Flux<DocumentType> findAll() {
        return hashOperations.values(KEY);
    }

    @Override
    public Mono<DocumentType> save(DocumentType documentType) {
        if (documentType.getName().isEmpty() || documentType.getExpiration().isEmpty())
            return Mono.error(new IllegalArgumentException("Cannot be saved: name and expiration are required, but one or both is empty."))
                       .thenReturn(documentType);

        if (documentType.getId() == null || documentType.getId().toString().isEmpty() ||
            (!documentType.getId().toString().startsWith(REDIS_PREFIX))) {

            String documentId = REDIS_PREFIX + UUID.randomUUID().toString().replaceAll("-", "");
                                /* ^ This is not the UUID came from the param-service, we add a prefix REDIS_PREFIX ("_R") */
            documentType.setId(documentId);
            documentType.setVersion(0);

            return Mono.defer(() -> addOrUpdateDocumentType(documentType, existsByName(documentType.getName())));
        } else {
            return findById(documentType.getId())
                    .flatMap(d -> {
                        if (d.getVersion() != documentType.getVersion()) {
                            return Mono.error(
                                    new OptimisticLockingFailureException(
                                            "This record has already been updated earlier by another object."));
                        } else {
                            documentType.setVersion(documentType.getVersion() + 1);

                            return Mono.defer(() -> {
                                Mono<Boolean> exists = Mono.just(false);

                                if (!d.getName().equals(documentType.getName())) {
                                    exists = existsByName(documentType.getName());
                                }

                                return addOrUpdateDocumentType(documentType, exists);
                            });
                        }
                    })
                    .switchIfEmpty(Mono.defer(() -> addOrUpdateDocumentType(documentType, existsByName(documentType.getName()))));
        }
    }

    @Override
    public Mono<DocumentType> findByName(String name) {
        return hashOperations.values(KEY)
                             .filter(d -> d.getName().equals(name))
                             .singleOrEmpty();
    }

    @Override
    public Mono<Boolean> existsById(String id) { return hashOperations.hasKey(KEY, id); }
    @Override
    public Mono<Boolean> existsByName(String name) { return findByName(name).hasElement(); }
    @Override
    public Mono<Long> count() { return hashOperations.values(KEY).count(); }
    @Override
    public Mono<Void> deleteAll() { return hashOperations.delete(KEY).then(); }
    @Override
    public Mono<Void> delete(DocumentType documentType) {
        return hashOperations.remove(KEY, documentType.getId()).then();
    }
    @Override
    public Mono<Void> deleteById(String id) { return hashOperations.remove(KEY, id).then(); }
    @Override
    public <S extends DocumentType> Flux<S> saveAll(Iterable<S> iterable) {
        return (Flux<S>) Flux.fromIterable(iterable).flatMap(d -> save(d));
    }
    @Override
    public Mono<Void> deleteAll(Iterable<? extends DocumentType> iterable) {
        Flux.fromIterable(iterable).flatMap(d -> save(d));

        return Mono.empty();
    }
    /**
     * These methods are not implemented because teh service does not need them. But can be extended if in the
     * future the service need them.
     */
    @Override
    public Mono<Void> deleteAllById(Iterable<? extends String> strings) { return null; }
    @Override
    public Mono<DocumentType> findById(Publisher<String> publisher) { return null; }
    @Override
    public Mono<Boolean> existsById(Publisher<String> publisher) { return null; }
    @Override
    public Flux<DocumentType> findAllById(Iterable<String> iterable) { return null; }
    @Override
    public Flux<DocumentType> findAllById(Publisher<String> publisher) { return null; }
    @Override
    public <S extends DocumentType> Flux<S> saveAll(Publisher<S> entityStream) { return null;}
    @Override
    public Mono<Void> deleteById(Publisher<String> publisher) { return null; }
    @Override
    public Mono<Void> deleteAll(Publisher<? extends DocumentType> publisher) { return null; }

    /**
     * Private utility method to add new DocumentType if not exist with name
     */
    private Mono<DocumentType> addOrUpdateDocumentType(DocumentType documentType, Mono<Boolean> exists) {
        return exists.flatMap(exist -> {
                    if (exist)
                        return Mono.error(new DuplicateKeyException("Duplicate key, Name: " + documentType.getName() + " exists."));
                    else
                        return hashOperations.put(KEY, documentType.getId(), documentType)
                                             .map(isSaved -> documentType);
                })
                .thenReturn(documentType);
    }

}
