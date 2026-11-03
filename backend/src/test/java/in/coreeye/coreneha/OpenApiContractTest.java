package in.coreeye.coreneha;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import in.coreeye.coreneha.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Exports the OpenAPI contract (contracts/openapi.json is generated from it and
 * the staff application's client is generated from that). Security filters are
 * not applied here; in a running server the document requires login.
 */
@SpringBootTest
class OpenApiContractTest extends PostgresTestBase {

    @Autowired WebApplicationContext context;

    @Test
    void exportContract() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        String json = mvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Path out = Path.of("target", "openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, json);
    }
}
