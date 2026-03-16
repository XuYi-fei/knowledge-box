package com.knowledgebox.service.apptool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class Md5DigestAppToolExecutorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Md5DigestAppToolExecutor executor = new Md5DigestAppToolExecutor();

    @Test
    void shouldReturnMd5DigestAsPrimaryTextField() throws Exception {
        var input = objectMapper.readTree("{\"text\":\"admin123\"}");
        var config = objectMapper.readTree("{}");

        var result = executor.execute(input, config);

        assertThat(result.path("text").asText()).isEqualTo("0192023a7bbd73250516f069df18b500");
        assertThat(result.path("digest").asText()).isEqualTo("0192023a7bbd73250516f069df18b500");
        assertThat(result.path("sourceText").asText()).isEqualTo("admin123");
        assertThat(executor.resultType()).isEqualTo("text");
        assertThat(executor.preview(result)).isEqualTo("0192023a7bbd73250516f069df18b500");
    }
}
