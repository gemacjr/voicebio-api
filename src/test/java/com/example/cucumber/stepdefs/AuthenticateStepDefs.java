package com.example.cucumber.stepdefs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

public class AuthenticateStepDefs extends StepDefs {

  @Autowired private MockMvc mockMvc;

  @When("I get authenticate")
  public void i_get_authenticate() throws Throwable {
    actions = mockMvc.perform(get("/api/authenticate"));
  }

  @Then("authenticate returns response with status No Content")
  public void authenticate_returns_response_with_status_no_content() throws Throwable {
    actions.andExpect(status().isNoContent());
  }
}
