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
 *  SystemDateRepository.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.repository

import com.ailegorreta.cacheservice.model.DayType
import com.ailegorreta.cacheservice.model.SystemDate
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono

/**
 * SystemDate redis repository. This repository is a CRUD reactive repository. Since Redis reactive does not
 * support reactive CRUD repositories we implemented one (i.e., DocumentTypeRepositoryImp
 *
 * note : redis does not support reactive repositories (and will not) because:
 * https://github.com/spring-projects/spring-data-redis/issues/1405
 *
 * Name is unique key with exception o DaysType.FESTIVO
 *
 * @project cache-service
 * @author rlh
 * @date September 2023
 */
interface SystemDateRepository : ReactiveCrudRepository<SystemDate, String> {
    fun findByName(name: DayType): Mono<SystemDate>
    fun existsByName(name: DayType): Mono<Boolean>

    /**
     * Kotlin Coroutines to handle reactive Redis Crud Repository
     */
    suspend fun kFindById(id: String): SystemDate?
    suspend fun kFindAll(): Flow<SystemDate>
    suspend fun <S : SystemDate> kSave(systemDate: S): S
    suspend fun kFindByName(name: DayType): SystemDate?
    suspend fun kExistsById(id: String): Boolean
    suspend fun kExistsByName(name: DayType): Boolean
    suspend fun kCount(): Long
    suspend fun kDeleteAll(): Void
    suspend fun kDelete(systemDate: SystemDate): Void
    suspend fun kDeleteById(id: String): Void
    suspend fun kSaveAll(iterable: Iterable<SystemDate>): Flow<SystemDate>
    suspend fun kDeleteAll(iterable: Iterable<SystemDate>): Void
}