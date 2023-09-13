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
 *  EventServiceTest.kt
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.service

import com.ailegorreta.cacheservice.config.ServiceConfig
import com.ailegorreta.commons.event.EventDTO
import com.ailegorreta.commons.event.EventType
import com.ailegorreta.commons.utils.HasLogger
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.stereotype.Service

/**
 * This service test is just to emulate how we can send an event for 'param-service' in order that the
 * cache-service listen to it.
 *
 * @project: cache-service
 * @autho: rlh
 * @date August 2023
 */
@Service
class EventServiceTest(private val streamBridge: StreamBridge,
                       private val serviceConfig: ServiceConfig,
                       private val mapper: ObjectMapper): HasLogger {

    private val coreName = "param-service"

    /**
     * This method sends an event using Spring cloud stream, i.e., streamBridge instance
     */
    fun sendEvent(correlationId: String = "CorrelationId-test",
                  userName: String = "Test",
                  eventName: String,
                  value: Any): EventDTO {
        val eventBody = mapper.readTree(mapper.writeValueAsString(value))
        val parentNode = mapper.createObjectNode()

        // Add the permit where notification will be sent
        parentNode.put("notificaFacultad", "NOTIFICA_PARAM")
        // parentNode.put("datos",  eventBody!!.toString())
        parentNode.set<JsonNode>("datos", eventBody!!)

        val event = EventDTO(correlationId = correlationId ?: "NA",
                            eventType = EventType.DB_STORE,
                            username = userName,
                            eventName = eventName,
                            applicationName = serviceConfig.appName!!,
                            coreName = coreName,
                            eventBody = parentNode)

        logger.debug("Will send use stream bridge:$streamBridge")

        val res = streamBridge.send("producerTest-out-0", event)
        logger.debug("Result for sending the message via streamBridge:$res")

        return event
    }

    /**
     * This method does not send and event (because the caller is going to use the KafkaTemplate instance) but just
     * create and instance of the message EventDTO
     */
    fun generateOnlyEvent(correlationId: String = "CorrelationId-test",
                          userName: String = "Test",
                          eventName: String,
                          value: Any): EventDTO {
        val eventBody = mapper.readTree(mapper.writeValueAsString(value))
        val parentNode = mapper.createObjectNode()

        // Add the permit where notification will be sent
        parentNode.put("notificaFacultad", "NOTIFICA_PARAM")
        parentNode.set<JsonNode>("datos", eventBody!!)

        val event = EventDTO(correlationId = correlationId ?: "NA",
            eventType = EventType.DB_STORE,
            username = userName,
            eventName = eventName,
            applicationName = serviceConfig.appName!!,
            coreName = coreName,
            eventBody = parentNode)

        logger.debug("Event created and not sent:$event")

        return event
    }


}
