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
*  EventLoggerService.kt
*
*  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
*/
package com.ailegorreta.cacheservice.service

import com.ailegorreta.cacheservice.model.SystemRate
import com.ailegorreta.cacheservice.repository.SystemRateRepository
import com.ailegorreta.commons.event.EventDTO
import com.ailegorreta.commons.utils.HasLogger
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.TextNode
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch


/**
 * EventService that receives the events and invalidates the Redis database
 *
 *  @author rlh
 *  @project : cache-service
 *  @date August 2023
 */
@Service
class EventService(private val systemRateRepository: SystemRateRepository,
                   private val cacheService: CacheService): HasLogger {

    var latch = CountDownLatch(1)

    /**
     * This function receives an event from the param-service that we need to synchronize the Redis in memory
     * database from the param-service Postgres database.
     */
    /* note: The @KafkaListener annotation must be uncommented just for the Kafka test (i.e., KakfkaTests.kt class)
     *       without the use os Spring cloud stream configuration
     */
    // @KafkaListener(topics = ["param-service"], groupId = "group-cache-service")
    fun processEvent(eventDTO: EventDTO): EventDTO? {
        logger.info("Maybe we invalidate Redis database with the event $eventDTO")
        if (eventDTO.eventName.contains("VARIABLE_SISTEMA")) {
            // we need to update the cache system variable
            var eventBody = eventDTO.eventBody as JsonNode      // as HashMap<*,*>
            var datos = eventBody["datos"] as JsonNode          // as HashMap<*,*>
            var name = datos["name"] as TextNode
            var rate = datos["rate"] as DoubleNode

            logger.debug("Modify a system variable $name with value:$rate")
            systemRateRepository.save(SystemRate (name.asText(), rate =  BigDecimal.valueOf(rate.asDouble())))
            latch.countDown()       // just for testing purpose
        } else if (eventDTO.eventName.contains("FECHA_SISTEMA")) {
            logger.debug("Modify a system date invalidate all redis system dates")
            cacheService.invalidateSystemDates()
            latch.countDown()       // just for testing purpose
        } else if (eventDTO.eventName.contains("TIPO_DOCUMENTO")) {
            logger.debug("Modify a document type invalidate all redis document types")
            cacheService.invalidateDocumentTypes()
            latch.countDown()       // just for testing purpose
        }
        return eventDTO


        return null
    }

    fun resetLatch() {
        latch = CountDownLatch(1)
    }
}
