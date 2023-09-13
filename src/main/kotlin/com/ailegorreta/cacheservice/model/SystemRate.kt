/* Copyright (c) 2022, LMASS Desarrolladores S.C.
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
 *  SystemRate.kt
 *
 *  Developed 2022 by LMASS Desarrolladores, S.C. www.lmass.com.mx
 */
package com.ailegorreta.cacheservice.model

import java.math.BigDecimal

/**
 * System rates: exchange rates and TIIF, etc
 *
 * Since we use redis reactive, noo @RedisHash annotation is needed.
 *
 * note: name is a unique identifier in SystemRate
 *
 * @project cache-server
 * @autho rlh
 * @date September 2023
 */
data class SystemRate(var id: String? = null,
                      var name: String = "",
                      var rate: BigDecimal = BigDecimal.ZERO,
                      var version: Int = 0) {       // This is for optimistic locking
    override fun hashCode(): Int = id.hashCode()
}

data class GraphqlResponseGetSystemRate constructor(val data: Data) {
    data class Data constructor(val systemRate: SystemRate)
}
