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
*  CacheService.kt
*
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
*/
package com.ailegorreta.cacheservice.service

import com.ailegorreta.cacheservice.model.*
import com.ailegorreta.cacheservice.repository.DocumentTypeRepository
import com.ailegorreta.cacheservice.repository.SystemDateRepository
import com.ailegorreta.cacheservice.repository.SystemRateRepository
import com.ailegorreta.cacheservice.repository.impl.DocumentTypeRepositoryImpl
import com.ailegorreta.commons.utils.HasLogger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.function.Function
import kotlin.math.abs

/**
 * CacheService retrieves all system parameters:
 *  - Variables
 *  - Dates
 *  - Document types
 *
 * note: before call param microservice, it checks existence in Redis memory database
 *
 *  @author rlh
 *  @project : cache-service
 *  @date September 2023
 */
@Service
class CacheService(val paramService: ParamService,
                   @Qualifier("systemRateRepositoryImpl") val systemRateRepository: SystemRateRepository,
                   @Qualifier("systemDateRepositoryImpl") val systemDateRepository: SystemDateRepository,
                   @Qualifier("documentTypeRepositoryImpl") val documentTypeRepository: DocumentTypeRepository) : HasLogger {
    private var today: LocalDate? = null
    private var systemDates: List<SystemDate> = emptyList()

    /**
     * Methods for SystemRates. System Rates example are all in Redis and just invalidate per Rate
     */

    /**
     * Method that saves a systemRates inside the redis database. It is calles after the systemRate is read
     * from param microservice, or when en event is received that some system variable was changed.
     */
    fun cacheSystemRate(systemRate: SystemRate): SystemRate? {
        return try {
            logger.debug("save the variable ${systemRate.name} in Redid database")
            systemRateRepository.save(systemRate).block()
            systemRate
        } catch (e: Exception) {
            logger.error("No se pudo almacenar correctamente la variable ${systemRate.name} en el cache: ${e.message}")
            null
        }
    }

    /**
     * Method that gets a systemRate from redis (if exists) or from param service
     */
    fun getSystemRate(name: String): Mono<SystemRate> {
        logger.debug("Try to get the variable $name")

        return systemRateRepository.findByName(name)
                                   .flatMap { systemRate: SystemRate ->
                                        logger.debug("Got the variable from cache")
                                        Mono.just(systemRate)
                                    }
                                    .switchIfEmpty(Mono.defer {
                                        val res = paramService.getFromParamsSystemRate(name)

                                        if (res != null) {
                                            logger.debug("Got variable {} with value {} from microservice. Store it in cache", name, res.rate)
                                            systemRateRepository.save(res)
                                        } else
                                            Mono.error(DataRetrievalFailureException("No variable $name found in SystemRate"))
                                    })
    }

    /**
     * Methods for system dates. In this case since we have few Holidays and SystemDates we almost don´t use
     * Redis database but use a list of SystemDate
     */

    /**
     * Reads all system dates from Redis  (if exists) otherwise it returns an empty list
     */
    private fun systemDatesCache(): List<SystemDate> {
        if (systemDates.isEmpty())
            try {
                val res = mutableListOf<SystemDate>()

                systemDateRepository.findAll().collectList().subscribe(res::addAll)
                // ^ Do not use block() because for testing purpose we are inside a blocking statement
                if (res.isEmpty()) {
                    systemDates = paramService.allSystemDates()
                    logger.debug("Read from  param microservice ${systemDates.size} and save them Redis cache")
                    systemDateRepository.saveAll(systemDates)
                } else
                    systemDates = res
                logger.debug("Read all system dates from cached. Read ${systemDates.count()} dates")
            } catch (e: Exception) {
                logger.error("No se pudo leer correctamente las fechas del sistema en el cache: ${e.message}")
                systemDates = emptyList()
            }

        return systemDates
    }

    /**
     * Some system date has been modified (i.e., received an event), therefore invalidate all systemDates from Redis
     */
    fun invalidateSystemDates() {
        try {
            logger.info("Invalidate all system dates from cached. Some date was changed.")
            systemDateRepository.deleteAll()
            systemDates = emptyList()
            today = null
        } catch (e: Exception) {
            logger.error("Could not delete all system dates from cache")
        }
    }

    /**
     * Gets today from the systemDates (if exists) otherwise return the machine date
     */
    private fun getToday(): LocalDate {
        if (today != null) return today!!

        val systemDate = systemDatesCache().find { systemDate -> systemDate.name == DayType.HOY }

        return if (systemDate != null)
            systemDate.day
        else {
            logger.error("Not found system date as HOY, use the computer date")
            LocalDate.now()
        }
    }

    /**
     * Validate if the date is holiday: is weekend or a holiday is declared in systemDates list.
     */
    fun isHoliday(day: LocalDate) =
                day.dayOfWeek.equals(DayOfWeek.SATURDAY) ||
                day.dayOfWeek.equals(DayOfWeek.SUNDAY) ||
                (systemDatesCache().find{ systemDate -> systemDate.name == DayType.FESTIVO &&
                                                        systemDate.day == day } != null)

    /**
     * This method gets today or a work day plus or minus 'days'. It validates that the day is not a holiday
     */
    fun getDay(days: Int): LocalDate {
        logger.info("Try to calculate the day after $days passed")

        var result = getToday()
        logger.info("The day is: $result")

        result = if (days > 0)
            result.plusDays(days.toLong())
        else
            result.minusDays(days.toLong())

        while (isHoliday(result)) {
            result = if (days > 0)
                result.plusDays(1)
            else
                result.minusDays(1)
        }

        return result
    }

    /**
     * This method adds from today a number of working days
     */
    fun addDay(days: Int): LocalDate {
        logger.debug("Try to calculate the day after $days working days")

        var result = getToday()
        logger.debug("Today is: {}", result)
        var numDays = abs(days)

        while (numDays > 0) {
            result = if (days > 0)
                result.plusDays(1)
            else
                result.minusDays(1)
            if (!isHoliday(result))
                numDays--
        }

        return result
    }

    /**
     * Methods for document types. In this type we suppose to have many DocumentTypes so we use a lot
     * the Redis memory database in each call
     */

    /**
     * Reads all document types from Redis  (if exists) otherwise it returns an empty list
     */
    private fun documentTypesCache(): Flux<DocumentType> {
        return try {
            documentTypeRepository.findAll()
        } catch(e: Exception) {
            logger.error("No se pudo leer correctamente los tipos de documentos del sistema en el cache: ${e.message}")
            Flux.error( DataRetrievalFailureException("No se pudo leer correctamente los tipos de documentos del sistema en el cache"))
        }
    }

    /**
     * Some document type has been modified (i.e., received an event), therefore invalidate all documentTypes from Redis
     */
    fun invalidateDocumentTypes() {
        try {
            logger.debug("Invalidate all document types from cached. Some data was changed.")
            documentTypeRepository.deleteAll()
        } catch (e: Exception) {
            logger.error("Could not delete all document types from cache")
        }
    }

    /**
     * This method gets all de documentType from cache, if not it read from the microservice
     */
    fun getDocumentTypes(): Mono<List<DocumentType>> {
        logger.debug("Read the document types")

        return documentTypesCache().collectList()
                                    .flatMap {
                                        val result = if (it.isEmpty()) {
                                            logger.debug("Read from param microservice")
                                            val documentTypes = paramService.allDocumentTypes()

                                            documentTypeRepository.saveAll(documentTypes).collectList()
                                        } else {
                                            logger.debug("Read from cache ${it.size}")
                                            Mono.just(it)
                                        }
                                        result
                                    }
    }

}
