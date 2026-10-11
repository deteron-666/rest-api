package com.mixamus.restapi.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void unexpectedErrorIsReturnedAsProblemDetailWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/failing"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Internal server error"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    /**
     * Внутренний (не static) класс: component scan такие не подхватывает, поэтому в контексты
     * остальных @SpringBootTest этот контроллер не попадёт.
     */
    @RestController
    class FailingController {

        @GetMapping("/failing")
        String fail() {
            throw new IllegalStateException("secret internal state");
        }
    }
}
