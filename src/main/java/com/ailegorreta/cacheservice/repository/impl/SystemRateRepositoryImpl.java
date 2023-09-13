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
 *  SystemRateRepositoryImpl.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.repository.impl;

import com.ailegorreta.cacheservice.model.SystemRate;
import com.ailegorreta.cacheservice.repository.SystemRateRepository;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Implementation for the CRUD reactive repository for SystemRates
 *
 * @project: cache-service
 * @author rlh
 * @date: September 2023
 */
@Repository("systemRateRepositoryImpl")
public class SystemRateRepositoryImpl implements SystemRateRepository {

    private final static String KEY = "SYSTEM_RATE";
    private final static String REDIS_PREFIX = "_R";
    private final ReactiveRedisOperations<String, SystemRate> redisOperations;
    private final ReactiveHashOperations<String, String, SystemRate> hashOperations;

    @Autowired
    public SystemRateRepositoryImpl(ReactiveRedisOperations<String, SystemRate> redisOperations) {
        this.redisOperations = redisOperations;
        this.hashOperations = redisOperations.opsForHash();
    }

    @Override
    public Mono<SystemRate> findById(String id) {
        return hashOperations.get(KEY, id);
    }

    @Override
    public Flux<SystemRate> findAll() {
        return hashOperations.values(KEY);
    }

    @Override
    public Mono<SystemRate> save(SystemRate systemRate) {
        if (systemRate.getName().isEmpty() || systemRate.getRate().equals(BigDecimal.ZERO))
            return Mono.error(new IllegalArgumentException("Cannot be saved: name and rate are required, but one or both is empty."))
                    .thenReturn(systemRate);

        if (systemRate.getId() == null || systemRate.getId().toString().isEmpty() ||
            (!systemRate.getId().toString().startsWith(REDIS_PREFIX))) {

            String systemRateId = REDIS_PREFIX + UUID.randomUUID().toString().replaceAll("-", "");
                                    /* ^ This is not the UUID came from the param-service, we add a prefix REDIS_PREFIX ("_R") */
            systemRate.setId(systemRateId);
            systemRate.setVersion(0);

            return Mono.defer(() -> addOrUpdateSystemRate(systemRate, existsByName(systemRate.getName())));
        } else {
            return findById(systemRate.getId())
                    .flatMap(r -> {
                        if (r.getVersion() != systemRate.getVersion()) {
                            return Mono.error(
                                    new OptimisticLockingFailureException(
                                            "This record has already been updated earlier by another object."));
                        } else {
                            systemRate.setVersion(systemRate.getVersion() + 1);

                            return Mono.defer(() -> {
                                Mono<Boolean> exists = Mono.just(false);

                                if (!r.getName().equals(systemRate.getName())) {
                                    exists = existsByName(systemRate.getName());
                                }

                                return addOrUpdateSystemRate(systemRate, exists);
                            });
                        }
                    })
                    .switchIfEmpty(Mono.defer(() -> addOrUpdateSystemRate(systemRate, existsByName(systemRate.getName()))));
        }
    }

    @Override
    public Mono<SystemRate> findByName(String name) {
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
    public Mono<Void> delete(SystemRate systemRate) {
        return hashOperations.remove(KEY, systemRate.getId()).then();
    }
    @Override
    public Mono<Void> deleteById(String id) { return hashOperations.remove(KEY, id).then(); }
    @Override
    public <S extends SystemRate> Flux<S> saveAll(Iterable<S> iterable) {
        return (Flux<S>) Flux.fromIterable(iterable).flatMap(d -> save(d));
    }
    @Override
    public Mono<Void> deleteAll(Iterable<? extends SystemRate> iterable) {
        Flux.fromIterable(iterable).flatMap(d -> delete(d));

        return Mono.empty();
    }

    /**
     * These methods are not implemented because teh service does not need them. But can be extended if in the
     * future the service need them.
     */
    @Override
    public Mono<Void> deleteAllById(Iterable<? extends String> strings) { return null; }
    @Override
    public <S extends SystemRate> Flux<S> saveAll(Publisher<S> publisher) { return null; }
    @Override
    public Mono<SystemRate> findById(Publisher<String> publisher) { return null; }
    @Override
    public Mono<Boolean> existsById(Publisher<String> publisher) { return null; }
    @Override
    public Flux<SystemRate> findAllById(Iterable<String> iterable) { return null; }
    @Override
    public Flux<SystemRate> findAllById(Publisher<String> publisher) { return null; }
    @Override
    public Mono<Void> deleteById(Publisher<String> publisher) { return null; }
    @Override
    public Mono<Void> deleteAll(Publisher<? extends SystemRate> publisher) { return null; }


    /**
     * Private utility method to add new DocumentType if not exist with name
     */
    private Mono<SystemRate> addOrUpdateSystemRate(SystemRate systemRate, Mono<Boolean> exists) {
        return exists.flatMap(exist -> {
                    if (exist)
                        return Mono.error(new DuplicateKeyException("Duplicate key, Name: " + systemRate.getName() + " exists."));
                    else
                        return hashOperations.put(KEY, systemRate.getId(), systemRate)
                                .map(isSaved -> systemRate);
                })
                .thenReturn(systemRate);
    }

}
