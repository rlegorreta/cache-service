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
 *  SystemDate.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import java.time.LocalDate

/**
 * System calendar. Today, re-process, yesterday, tomorrow, holidays, etc.
 *
 * Since we use redis reactive no @RedisHas annotation is needed.
 *
 * The name is unique key except DayType.FESTIVO
 *
 * note: GenericJackson2JsonRedisSerializer instance did not know to use the jsr310 from Jackson to read and
 * write LocalDateTime data.
 * see: https://stackoverflow.com/questions/53267203/spring-data-redis-issue-while-storing-date
 *
 * @project cache-service
 * @autho rlh
 * @date September 2023
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SystemDate(@JsonProperty("id")  var id: String?,
                      @JsonProperty("name")  var name: DayType = DayType.HOY,
                      @JsonProperty("day")
                      @JsonDeserialize(using = LocalDateDeserializer::class)
                      @JsonSerialize(using = LocalDateSerializer::class)
                      var day: LocalDate = LocalDate.now(),
                      @JsonProperty("version")  var version: Int = 0) {  // This is for optimistic locking

    override fun hashCode(): Int = id.hashCode()
}

enum class DayType {
    HOY, MANANA, AYER, REPROCESO, FESTIVO
}

data class GraphqlResponseSystemDates constructor(val data: Data) {
    data class Data constructor(val systemDates: List<SystemDate>)
}
