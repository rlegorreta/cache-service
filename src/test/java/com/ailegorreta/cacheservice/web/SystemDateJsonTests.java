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
 *  SystemRateRepositoryTests.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice.web;

import com.ailegorreta.cacheservice.model.DayType;
import com.ailegorreta.cacheservice.model.SystemDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example for a JsonTest for some Redis DTOs, many others can be added
 *
 * In this case we check that DateTime is serializable correctly in SystemDate DTO
 *
 * @project cache-service
 * @author rlh
 * @date September 2023
 */
@JsonTest
@ContextConfiguration(classes = SystemRateJsonTests.class)
@ActiveProfiles("integration-tests")
public class SystemDateJsonTests {
    @Autowired
    public JacksonTester<SystemDate> json;

    @Test
    void testSerialize() throws Exception {
        var systemDate = new SystemDate(UUID.randomUUID().toString(), DayType.HOY, LocalDate.now(), 0);
        var jsonContent = json.write(systemDate);

        assertThat(jsonContent).extractingJsonPathStringValue("@.id")
                .isEqualTo(systemDate.getId().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.name")
                .isEqualTo(systemDate.getName().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.day")
                .isEqualTo(systemDate.getDay().toString());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.version")
                .isEqualTo(systemDate.getVersion());
    }

    @Test
    void testDeserialize() throws Exception {
        var systemDate = new SystemDate(UUID.randomUUID().toString(), DayType.HOY, LocalDate.now(), 0);
        var content = """
                {
                    "id": 
                    """ + "\"" + systemDate.getId() + "\"," + """
                    "name": "HOY",
                    "day": 
                    """ + "\"" + systemDate.getDay() + "\"" + "," + """
                    "version" : 
                    """ + systemDate.getVersion() + """
                }
                """;
        assertThat(json.parse(content))
                        .usingRecursiveComparison()
                        .isEqualTo(systemDate);
    }
}

