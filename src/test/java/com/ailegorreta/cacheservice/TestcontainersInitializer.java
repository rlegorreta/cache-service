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
 *  TestcontainersInitializer.java
 *
 *  Developed 2023 by LegoSoftSoluciones, S.C. www.legosoft.com.mx
 */
package com.ailegorreta.cacheservice;

import com.ailegorreta.commons.event.EventDTO;
import com.ailegorreta.commons.event.EventDTODeSerializer;
import com.ailegorreta.commons.event.EventDTOSerializer;
import com.ailegorreta.resourceserver.utils.HasLogger;
import com.redis.testcontainers.RedisContainer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is a class to start the containers only once for all tests. Redis and Kafka containers
 *
 * Algo it starts the container in parallel
 *
 * @project cache-service
 * @author rlh
 * @date September 2023
 */
class TestcontainersInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>,
                                            HasLogger {

    static final Integer REDIS_PORT = 6739;

    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.0"))
                                        .withExposedPorts(REDIS_PORT);

    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

    static {
        Startables.deepStart(redis, kafka).join();
        await().until(redis::isRunning);
        await().until(kafka::isRunning);
    }

    /**
     * Kafka container Test configuration class (optional)
     */
    @TestConfiguration
    static class KafkaTestContainersConfiguration {
        /** These configurations for consumers are not necessary (but leave the for example purpose) since the
         *  consumer configuration is taken from the application.yml file
         */
        @Bean
        ConcurrentKafkaListenerContainerFactory<Integer, String> kafkaListenerContainerFactory() {
            ConcurrentKafkaListenerContainerFactory<Integer, String> factory = new ConcurrentKafkaListenerContainerFactory<>();

            factory.setConsumerFactory(consumerFactory());

            return factory;
        }

        @Bean
        ConsumerFactory<Integer, String> consumerFactory() {
            return new DefaultKafkaConsumerFactory<>(consumerConfigs());
        }

        @Bean
        Map<String, Object> consumerConfigs() {
            HashMap<String, Object> props = new HashMap<>();

            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, EventDTODeSerializer.class);

            return props;
        }

        @Bean
        ProducerFactory<String, EventDTO> producerFactory() {
            HashMap<String, Object> configProps = new HashMap<>();

            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventDTOSerializer.class);

            return new DefaultKafkaProducerFactory<>(configProps);
        }

        @Bean
        KafkaTemplate<String, EventDTO> kafkaTemplate() {
            return new KafkaTemplate<>(producerFactory());
        }
    }


    /**
     * Sets all environment variables without the need to create a
     * @ActiveProfiles("integration-flyway")
     *
     * @param ctx the application to configure
     */
    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        getLogger().info("Kafka test container bootstrap-servers: {}", kafka.getBootstrapServers());
        getLogger().info("Redis test container host: {}", redis.getHost());
        getLogger().info("Redis test container mapped port: {}", redis.getMappedPort(REDIS_PORT));
    }

    /**
     * Sets all environment variables without the need to create a
     * @ActiveProfiles("integration-test")
     *
     * note: Do NOT use public void initialize(ConfigurableApplicationContext ctx) because
     *       for Redis container it does not work (i.e. it tries to connect before)
     */
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.kafka.binder.brokers=", () -> kafka.getBootstrapServers());
        registry.add("spring.data.host", () -> redis.getHost());
        registry.add("spring.data.port", () -> redis.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.url", () -> String.format(
                            "redis://%s:%s",
                            redis.getHost(),
                            redis.getMappedPort(REDIS_PORT)
                        ));
    }

    @NotNull
    @Override
    public Logger getLogger() { return HasLogger.DefaultImpls.getLogger(this); }
}