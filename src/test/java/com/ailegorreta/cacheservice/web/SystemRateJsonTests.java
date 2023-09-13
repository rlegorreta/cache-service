package com.ailegorreta.cacheservice.web;

import com.ailegorreta.cacheservice.model.SystemRate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example for a JsonTest for some Redis DTOs, many others can be added
 *
 * @project cache-service
 * @author rlh
 * @date September 2023
 */
@JsonTest
@ContextConfiguration(classes = SystemRateJsonTests.class)
@ActiveProfiles("integration-tests")
public class SystemRateJsonTests {
    @Autowired
    public JacksonTester<SystemRate> json;

    @Test
    void testSerialize() throws Exception {
        var systemRate = new SystemRate(UUID.randomUUID().toString(), "TestRate", BigDecimal.valueOf(20.45), 0);
        var jsonContent = json.write(systemRate);

        assertThat(jsonContent).extractingJsonPathStringValue("@.id")
                .isEqualTo(systemRate.getId().toString());
        assertThat(jsonContent).extractingJsonPathStringValue("@.name")
                .isEqualTo(systemRate.getName());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.rate")
                .isEqualTo(systemRate.getRate().doubleValue());
        assertThat(jsonContent).extractingJsonPathNumberValue("@.version")
                .isEqualTo(systemRate.getVersion());
    }

    @Test
    void testDeserialize() throws Exception {
        var systemRate = new SystemRate(UUID.randomUUID().toString(), "Test Rate", BigDecimal.valueOf(20.45), 0);
        var content = """
                {
                    "id": 
                    """ + "\"" + systemRate.getId() + "\"," + """
                    "name": "Test Rate",
                    "rate": 
                    """ + systemRate.getRate() + "," + """
                    "version" : 
                    """ + systemRate.getVersion() + """
                }
                """;
        assertThat(json.parse(content))
                       .usingRecursiveComparison()
                       .isEqualTo(systemRate);
    }
}
