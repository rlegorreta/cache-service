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
 *  DocumentTypeRepositoryImpl.kt
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.repository.impl

import com.ailegorreta.cacheservice.model.DocumentType
import com.ailegorreta.cacheservice.repository.DocumentTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingle
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
 * Implementation for the CRUD reactive repository for DocumentType
 *
 * @project: cache-service
 * @author rlh
 * @date: September 2023
 */
@Repository("documentTypeRepositoryImpl")
class DocumentTypeRepositoryImpl @Autowired constructor(redisOperations: ReactiveRedisOperations<String, DocumentType>) :
                                DocumentTypeRepository {
    private val hashOperations: ReactiveHashOperations<String, String?, DocumentType>

    init {
        hashOperations = redisOperations.opsForHash()
    }

    override fun findById(id: String): Mono<DocumentType> {
        return hashOperations[KEY, id]
    }

    override suspend fun kFindById(id: String): DocumentType? {
        return findById(id).awaitFirstOrNull()
    }

    override fun findAll(): Flux<DocumentType> {
        return hashOperations.values(KEY)
    }

    override suspend fun kFindAll(): Flow<DocumentType> {
        return findAll().asFlow()
    }

    override fun <D: DocumentType>save(documentType: D): Mono<D> {
        if (documentType.name.isEmpty() || documentType.expiration.isEmpty())
            return Mono.error<Any>(IllegalArgumentException("Cannot be saved: name and expiration are required, but one or both is empty."))
                        .thenReturn(documentType)
        return if (documentType.id == null || documentType.id.toString().isEmpty() ||
                    !documentType.id.toString().startsWith(REDIS_PREFIX)) {
            val documentId = REDIS_PREFIX + UUID.randomUUID().toString().replace("-".toRegex(), "")
            /* ^ This is not the UUID came from the param-service, we add a prefix REDIS_PREFIX ("_R") */
            documentType.id = documentId
            documentType.version = 0
            Mono.defer {
                addOrUpdateDocumentType( documentType, existsByName(documentType.name))
            }
        } else {
            findById(documentType.id!!)
                .flatMap { (_, name, _, version): DocumentType ->
                    if (version != documentType.version) {
                        return@flatMap Mono.error(OptimisticLockingFailureException(
                                "This record has already been updated earlier by another object."))
                    } else {
                        documentType.version = documentType.version + 1
                        return@flatMap Mono.defer {
                            var exists = Mono.just(false)
                            if (name != documentType.name) {
                                exists = existsByName(documentType.name)
                            }
                            addOrUpdateDocumentType(documentType, exists)
                        }
                    }
                }
                .switchIfEmpty(Mono.defer {
                    addOrUpdateDocumentType(documentType, existsByName(documentType.name))
                })
        }
    }

    override suspend fun <D: DocumentType> kSave(documentType: D): D {
        return save(documentType).awaitSingle()
    }

    override fun findByName(name: String): Mono<DocumentType> {
        return hashOperations.values(KEY)
                             .filter { (_, name1): DocumentType -> name1 == name }
                             .singleOrEmpty()
    }

    override suspend fun kFindByName(name: String): DocumentType? {
        return findByName(name).awaitFirstOrNull()
    }

    override fun existsById(id: String): Mono<Boolean> {
        return hashOperations.hasKey(KEY, id)
    }

    override suspend fun kExistsById(id: String): Boolean {
        return existsById(id).awaitSingle()
    }

    override fun existsByName(name: String): Mono<Boolean> {
        return findByName(name).hasElement()
    }

    override suspend fun kExistsByName(name: String): Boolean {
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

    override fun delete(documentType: DocumentType): Mono<Void> {
        return hashOperations.remove(KEY, documentType.id).then()
    }

    override suspend fun kDelete(documentType: DocumentType): Void {
        return delete(documentType).awaitSingle()
    }

    override fun deleteById(id: String): Mono<Void> {
        return hashOperations.remove(KEY, id).then()
    }

    override suspend fun kDeleteById(id: String): Void {
        return deleteById(id).awaitSingle()
    }

    override fun <D : DocumentType> saveAll(iterable: Iterable<D>): Flux<D> {
        return Flux.fromIterable(iterable).flatMap { d: D ->
            save(d)
        }
    }

    override suspend fun kSaveAll(iterable: Iterable<DocumentType>): Flow<DocumentType> {
        return saveAll(iterable).asFlow()
    }

    override fun deleteAll(iterable: Iterable<DocumentType>): Mono<Void> {
        Flux.fromIterable(iterable).flatMap { d: DocumentType ->
            save(d)
        }
        return Mono.empty()
    }

    override suspend fun kDeleteAll(iterable: Iterable<DocumentType>): Void {
        return deleteAll(iterable).awaitSingle()
    }

    /**
     * These methods are not implemented because teh service does not need them. But can be extended if in the
     * future the service need them.
     */
    override fun deleteAllById(strings: Iterable<String>): Mono<Void> {
        return Mono.empty()
    }

    override fun findById(publisher: Publisher<String>): Mono<DocumentType> {
        return Mono.empty()
    }

    override fun existsById(publisher: Publisher<String>): Mono<Boolean> {
        return Mono.empty()
    }

    override fun findAllById(iterable: Iterable<String>): Flux<DocumentType> {
        return Flux.empty()
    }

    override fun findAllById(publisher: Publisher<String>): Flux<DocumentType> {
        return Flux.empty()
    }

    override fun <S : DocumentType?> saveAll(entityStream: Publisher<S>): Flux<S> {
        return Flux.empty()
    }

    override fun deleteById(publisher: Publisher<String>): Mono<Void> {
        return Mono.empty()
    }

    override fun deleteAll(publisher: Publisher<out DocumentType>): Mono<Void> {
        return Mono.empty()
    }

    /**
     * Private utility method to add new DocumentType if not exist with name
     */
    private fun <D: DocumentType>addOrUpdateDocumentType(documentType: D, exists: Mono<Boolean>): Mono<D> {
        return exists.flatMap { exist: Boolean ->
                if (exist)
                    return@flatMap Mono.error<DocumentType>(
                        DuplicateKeyException("Duplicate key, Name: " + documentType.name + " exists."))
                else
                    return@flatMap hashOperations.put(KEY,documentType.id!!, documentType)
                    .map<DocumentType> { isSaved: Boolean? -> documentType }
            }
            .thenReturn(documentType)
    }

    companion object {
        private const val KEY = "DOCUMENT_TYPE"
        private const val REDIS_PREFIX = "_R"
    }
}
