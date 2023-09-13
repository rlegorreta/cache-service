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
 *  SystemDateRepositoryImpl.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.repository.impl;

import com.ailegorreta.cacheservice.model.DayType;
import com.ailegorreta.cacheservice.model.SystemDate;
import com.ailegorreta.cacheservice.repository.SystemDateRepository;
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
 * Implementation for the CRUD reactive repository for SystemDate
 *
 * @project: cache-service
 * @author rlh
 * @date: September 2023
 */
@Repository("systemDateRepositoryImpl")
public class SystemDateRepositoryImpl implements SystemDateRepository {

    private final static String KEY = "SYSTEM_DATE";
    private final static String REDIS_PREFIX = "_R";
    private final ReactiveRedisOperations<String, SystemDate> redisOperations;
    private final ReactiveHashOperations<String, String, SystemDate> hashOperations;

    @Autowired
    public SystemDateRepositoryImpl(ReactiveRedisOperations<String, SystemDate> redisOperations) {
        this.redisOperations = redisOperations;
        this.hashOperations = redisOperations.opsForHash();
    }

    @Override
    public Mono<SystemDate> findById(String id) {
        return hashOperations.get(KEY, id);
    }

    @Override
    public Flux<SystemDate> findAll() {
        return hashOperations.values(KEY);
    }

    @Override
    public Mono<? extends SystemDate> save(SystemDate systemDate) {
        if (systemDate.getName() == null || systemDate.getDay() == null)
            return Mono.error(new IllegalArgumentException("Cannot be saved: date type and date are required, but one or both is empty."))
                       .thenReturn(systemDate);

        if (systemDate.getId() == null || systemDate.getId().toString().isEmpty() ||
            (!systemDate.getId().toString().startsWith(REDIS_PREFIX))) {
            String systemDateId = REDIS_PREFIX + UUID.randomUUID().toString().replaceAll("-", "");
            /* ^ This is not the UUID came from the param-service, we add a prefix REDIS_PREFIX ("_R") */
            systemDate.setId(systemDateId);
            systemDate.setVersion(0);

            return Mono.defer(() -> addOrUpdateSystemDate(systemDate, existsByName(systemDate.getName())));
        } else {
            return findById(systemDate.getId())
                    .flatMap(d -> {
                        if (d.getVersion() != systemDate.getVersion()) {
                            return Mono.error(
                                    new OptimisticLockingFailureException(
                                            "This record has already been updated earlier by another object."));
                        } else {
                            systemDate.setVersion(systemDate.getVersion() + 1);

                            return Mono.defer(() -> {
                                Mono<Boolean> exists = Mono.just(false);

                                if (!d.getName().equals(systemDate.getName())) {
                                    exists = existsByName(systemDate.getName());
                                }

                                return addOrUpdateSystemDate(systemDate, exists);
                            });
                        }
                    })
                    .switchIfEmpty(Mono.defer(() -> addOrUpdateSystemDate(systemDate, existsByName(systemDate.getName()))));
        }
    }

    @Override
    public Mono<SystemDate> findByName(DayType name) {
        return hashOperations.values(KEY)
                             .filter(d -> d.getName().equals(name))
                             .singleOrEmpty();
    }

    @Override
    public Mono<Boolean> existsById(String id) { return hashOperations.hasKey(KEY, id); }
    @Override
    public Mono<Boolean> existsByName(DayType name) {
        if (name.equals(DayType.FESTIVO))
            return Mono.just(false);   // DayType.FESTIVO can be repeated
        return findByName(name).hasElement();
    }
    @Override
    public Mono<Long> count() { return hashOperations.values(KEY).count(); }
    @Override
    public Mono<Void> deleteAll() { return hashOperations.delete(KEY).then(); }
    @Override
    public Mono<Void> delete(SystemDate systemDate) {
        return hashOperations.remove(KEY, systemDate.getId()).then();
    }
    @Override
    public Mono<Void> deleteById(String id) { return hashOperations.remove(KEY, id).then(); }
    @Override
    public <S extends SystemDate> Flux<S> saveAll(Iterable<S> iterable) {
         return (Flux<S>) Flux.fromIterable(iterable).flatMap(d -> save(d));
    }
    @Override
    public Mono<Void> deleteAll(Iterable<? extends SystemDate> iterable) {
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
    public <S extends SystemDate> Flux<S> saveAll(Publisher<S> publisher) { return null; }
    @Override
    public Mono<SystemDate> findById(Publisher<String> publisher) { return null; }
    @Override
    public Mono<Boolean> existsById(Publisher<String> publisher) { return null; }
    @Override
    public Flux<SystemDate> findAllById(Iterable<String> iterable) { return null; }
    @Override
    public Flux<SystemDate> findAllById(Publisher<String> publisher) { return null; }
    @Override
    public Mono<Void> deleteById(Publisher<String> publisher) { return null; }
    @Override
    public Mono<Void> deleteAll(Publisher<? extends SystemDate> publisher) { return null; }


    /**
     * Private utility method to add new DocumentType if not exist with name
     */
    private Mono<SystemDate> addOrUpdateSystemDate(SystemDate systemDate, Mono<Boolean> exists) {
        return exists.flatMap(exist -> {
                    if (exist)
                        return Mono.error(new DuplicateKeyException("Duplicate key, Name: " + systemDate.getName() + " exists." + systemDate.getId()));
                    else
                        return hashOperations.put(KEY, systemDate.getId(), systemDate)
                                             .map(isSaved -> systemDate);
                })
                .thenReturn(systemDate);
    }

}
