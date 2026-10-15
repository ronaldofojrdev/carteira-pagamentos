package dev.ronaldofojr.wallet.account.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ronaldofojr.wallet.account.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AccountControllerIT extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;

    @Test
    void createsAndReadsAnAccount() throws Exception {
        String response = mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Carlos\",\"initialBalance\":150.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(150.5))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mvc.perform(get("/accounts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Carlos"));
        mvc.perform(get("/accounts/" + id + "/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rejectsBlankOwnerAndNegativeBalance() throws Exception {
        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"\",\"initialBalance\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerName\":\"Dani\",\"initialBalance\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        mvc.perform(get("/accounts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
