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
 *  SystemDateRepositoryImpl.kt
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.repository.impl

import com.ailegorreta.cacheservice.model.DayType
import com.ailegorreta.cacheservice.model.SystemDate
import com.ailegorreta.cacheservice.repository.SystemDateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.reactivestreams.Publisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.redis.core.ReactiveHashOperations
import org.springframework.data.redis.core.ReactiveRedisOperations
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Implementation for the CRUD reactive repository for SystemDate
 *
 * @project: cache-service
 * @author rlh
 * @date: September 2023
 */
@Repository("systemDateRepositoryImpl")
class SystemDateRepositoryImpl @Autowired constructor(redisOperations: ReactiveRedisOperations<String, SystemDate>) :
    SystemDateRepository {

    private val hashOperations: ReactiveHashOperations<String, String, SystemDate>

    init {
        hashOperations = redisOperations.opsForHash()
    }
    override fun findById(id: String): Mono<SystemDate> {
        return hashOperations[KEY, id]
    }

    override suspend fun kFindById(id: String): SystemDate? {
        return findById(id).awaitSingleOrNull()
    }

    override fun findAll(): Flux<SystemDate> {
        return hashOperations.values(KEY)
    }

    override suspend fun kFindAll(): Flow<SystemDate> {
        return findAll().asFlow()
    }
    override fun <S: SystemDate> save(systemDate: S): Mono<S> {
        if (systemDate.name == null || systemDate.day == null)
            return Mono.error<Any>(IllegalArgumentException("Cannot be saved: date type and date are required, but one or both is empty."))
                        .thenReturn(systemDate)
        return if (systemDate.id == null || systemDate.id.toString().isEmpty() ||
                    !systemDate.id.toString().startsWith(REDIS_PREFIX)) {
            val systemDateId = REDIS_PREFIX + UUID.randomUUID().toString().replace("-".toRegex(), "")
            /* ^ This is not the UUID came from the param-service, we add a prefix REDIS_PREFIX ("_R") */
            systemDate.id = systemDateId
            systemDate.version = 0
            Mono.defer {
                addOrUpdateSystemDate(systemDate,existsByName(systemDate.name))
            }
        } else {
            findById(systemDate.id!!)
                .flatMap { (_, name, _, version): SystemDate ->
                    if (version != systemDate.version) {
                        return@flatMap Mono.error(OptimisticLockingFailureException(
                                "This record has already been updated earlier by another object."))
                    } else {
                        systemDate.version = systemDate.version + 1
                        return@flatMap Mono.defer {
                            var exists = Mono.just(false)
                            if (name != systemDate.name) {
                                exists = existsByName(systemDate.name)
                            }
                            addOrUpdateSystemDate(systemDate, exists)
                        }
                    }
                }
                .switchIfEmpty(Mono.defer {
                    addOrUpdateSystemDate(systemDate,existsByName(systemDate.name))
                })
        }
    }

    override suspend fun <S: SystemDate> kSave(systemDate: S): S {
        return save(systemDate).awaitSingle()
    }

    override fun findByName(name: DayType): Mono<SystemDate> {
        return hashOperations.values(KEY)
                            .filter { (_, name1): SystemDate -> name1 == name }
                            .singleOrEmpty()
    }

    override suspend fun kFindByName(name: DayType): SystemDate? {
        return findByName(name).awaitFirstOrNull()
    }

    override fun existsById(id: String): Mono<Boolean> {
        return hashOperations.hasKey(KEY, id)
    }

    override suspend fun kExistsById(id: String): Boolean {
        return existsById(id).awaitSingle()
    }

    override fun existsByName(name: DayType): Mono<Boolean> {
        return if (name == DayType.FESTIVO) Mono.just(false) else findByName(name).hasElement() // DayType.FESTIVO can be repeated
    }

    override suspend fun kExistsByName(name: DayType): Boolean {
        return existsByName(name).awaitSingle()
    }

    override fun count(): Mono<Long> {
        return hashOperations.values(KEY).count()
    }

    override suspend fun kCount(): Long {
        return count().awaitSingle()
    }

    override fun deleteAll(): Mono<Void> {
        return hashOperations.delete(KEY).then()
    }

    override suspend fun kDeleteAll(): Void {
        return deleteAll().awaitSingle()
    }

    override fun delete(systemDate: SystemDate): Mono<Void> {
        return hashOperations.remove(KEY, systemDate.id).then()
    }

    override suspend fun kDelete(systemDate: SystemDate): Void {
        return delete(systemDate).awaitSingle()
    }

    override fun deleteById(id: String): Mono<Void> {
        return hashOperations.remove(KEY, id).then()
    }

    override suspend fun kDeleteById(id: String): Void {
        return deleteById(id).awaitSingle()
    }

    override fun <S : SystemDate> saveAll(iterable: Iterable<S>): Flux<S> {
        return Flux.fromIterable(iterable).flatMap { d: S ->
            save(d)
        }
    }

    override suspend fun kSaveAll(iterable: Iterable<SystemDate>): Flow<SystemDate> {
        return saveAll(iterable).asFlow()
    }

    override fun deleteAll(iterable: Iterable<SystemDate>): Mono<Void> {
        Flux.fromIterable(iterable).flatMap { d: SystemDate -> delete(d) }
        return Mono.empty()
    }

    override suspend fun kDeleteAll(iterable: Iterable<SystemDate>): Void {
        return deleteAll(iterable).awaitSingle()
    }

    /**
     * These methods are not implemented because teh service does not need them. But can be extended if in the
     * future the service need them.
     */
    override fun deleteAllById(strings: Iterable<String>): Mono<Void> {
        return Mono.empty()
    }

    override fun <S : SystemDate?> saveAll(publisher: Publisher<S>): Flux<S> {
        return Flux.empty()
    }

    override fun findById(publisher: Publisher<String>): Mono<SystemDate> {
        return Mono.empty()
    }

    override fun existsById(publisher: Publisher<String>): Mono<Boolean> {
        return  Mono.empty()
    }

    override fun findAllById(iterable: Iterable<String>): Flux<SystemDate> {
        return Flux.empty()
    }

    override fun findAllById(publisher: Publisher<String>): Flux<SystemDate> {
        return Flux.empty()
    }

    override fun deleteById(publisher: Publisher<String>): Mono<Void> {
        return Mono.empty()
    }

    override fun deleteAll(publisher: Publisher<out SystemDate>): Mono<Void> {
        return Mono.empty()
    }

    /**
     * Private utility method to add new DocumentType if not exist with name
     */
    private fun <S: SystemDate>addOrUpdateSystemDate(systemDate: S, exists: Mono<Boolean>): Mono<S> {
        return exists.flatMap { exist: Boolean ->
                if (exist)
                    return@flatMap Mono.error<SystemDate>(
                        DuplicateKeyException("Duplicate key, Name: " + systemDate.name + " exists." + systemDate.id))
                else
                    return@flatMap hashOperations.put(KEY, systemDate.id!!, systemDate)
                .map<SystemDate> { isSaved: Boolean? -> systemDate }
            }
            .thenReturn(systemDate)
    }

    companion object {
        private const val KEY = "SYSTEM_DATE"
        private const val REDIS_PREFIX = "_R"
    }
}