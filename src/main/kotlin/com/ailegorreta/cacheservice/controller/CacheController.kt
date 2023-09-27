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
 *  CacheController.kt
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.controller

import com.ailegorreta.cacheservice.model.DocumentType
import com.ailegorreta.cacheservice.service.CacheService
import com.ailegorreta.commons.utils.HasLogger
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDate

/**
 * This is the API REST that can receive the Cache controller. Every REST call is checked first if the data exist
 * in the Redis memory database. If exists then the data is returned, else the source microservice is called for the
 * data and stored in Redis database for subsequents calls
 *
 * @project: cache-service
 * @author: rlh
 * @date: September 2023
 */
@CrossOrigin
@RestController
@RequestMapping("/cache")
class CacheController(val cacheService: CacheService): HasLogger {

    @GetMapping("/sysvar", produces = ["application/json"])
    fun getVariableSystem(@RequestParam(required=true) nombre: String): Mono<BigDecimal> {
        return cacheService.getSystemRate(nombre)
                           .flatMap { systemRate -> Mono.just(systemRate.rate) }
                           .switchIfEmpty(Mono.just(BigDecimal.ZERO))
    }

    @GetMapping("/day", produces = ["application/json"])
    suspend fun getDay(@RequestParam(required=true) days: Int): LocalDate {
        logger.debug("Se requiere el dia del calendario despues de : $days dia(s)")

        return cacheService.getDay(days)
    }

    @GetMapping("/addday", produces = ["application/json"])
    suspend fun addDay(@RequestParam(required=true) days: Int): LocalDate {
        return cacheService.addDay(days)
    }

    @GetMapping("/holiday", produces = ["application/json"])
    suspend fun isHoliday(@RequestParam(required=true) day: LocalDate): Boolean {
        logger.debug("Se valida si es festivo $day")
        return cacheService.isHoliday(day)
    }

    @GetMapping("/doctypes", produces = ["application/json"])
    suspend fun allDocumentTypes(): List<DocumentType> {
        return cacheService.getDocumentTypes()
    }

    /**
     * This REST is just for development debug purpose. When the Redis database is invalidates is bias receiving
     * Kafka message from the param service and not via REST
     */
    @PostMapping("/invalid", produces = ["application/json"])
    fun invalidateAll(): LocalDate {
        logger.debug("Se invalidan las fechas y documentTypes en el caché ")
        cacheService.invalidateSystemDates()
        cacheService.invalidateDocumentTypes()

        return LocalDate.now()
    }

    @PostMapping("/invalidDates", produces = ["application/json"])
    fun invalidateDates(): LocalDate {
        logger.debug("Se invalidan las fechas en el caché ")
        cacheService.invalidateSystemDates()

        return LocalDate.now()
    }

    @PostMapping("/invalidDocuments", produces = ["application/json"])
    fun invalidateDocumentTypes(): LocalDate {
        logger.debug("Se invalidan los documentTypes en el caché ")
        cacheService.invalidateDocumentTypes()

        return LocalDate.now()
    }
}
