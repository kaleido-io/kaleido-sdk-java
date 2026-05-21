// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.componenttest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kaleido.wfe.sdk.client.WFEWebSocketClient;
import io.kaleido.wfe.sdk.componenttest.harness.TestWorkflowEngine;
import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.handlers.HandlerSetFor;
import io.kaleido.wfe.sdk.protocol.JSON;
import io.kaleido.wfe.sdk.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

import java.net.URI;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Component tests against the erc20-token workflow.
 * Exercises: multi-stage flows (deploy, mint, transfer), JSON Patch state updates.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ERC20ComponentTest {

    private TestWorkflowEngine wfe;
    private WFEWebSocketClient client;
    private String workflowId;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenInput(
            @JsonProperty("stageDirector") StageDirector stageDirector,
            String tokenName,
            String tokenSymbol,
            Integer initialSupply,
            String to,
            Integer amount,
            String from,
            Object txData
    ) implements WithStageDirector {
        @Override
        public StageDirector getStageDirector() {
            return stageDirector;
        }
    }

    @BeforeAll
    void setup() throws Exception {
        Assumptions.assumeTrue(TestWorkflowEngine.isAvailable(),
                "Docker not available -- component tests require Docker");
        wfe = TestWorkflowEngine.start();

        var handler = new DirectedTransactionHandler<TokenInput>(
                "token-handler", TokenInput.class, Map.of(
                "prepare-deploy", DirectedActionConfig.parallel(input -> {
                    var output = JSON.MAPPER.createObjectNode();
                    output.put("bytecode", "0x6080...");
                    var args = output.putArray("constructor_args");
                    args.add(input.tokenName());
                    args.add(input.tokenSymbol());
                    args.add(input.initialSupply());
                    return EvalResult.complete();
                }),
                "submit-deploy", DirectedActionConfig.parallel(input -> {
                    var output = JSON.MAPPER.createObjectNode();
                    output.put("contractAddress", "0xabc123");
                    output.put("txHash", "0xdef456");
                    return EvalResult.complete();
                }),
                "prepare-mint", DirectedActionConfig.parallel(input -> {
                    var output = JSON.MAPPER.createObjectNode();
                    output.put("encodedTx", "0xmint_calldata");
                    output.put("to", input.to());
                    output.put("amount", input.amount());
                    return EvalResult.complete();
                }),
                "submit-mint", DirectedActionConfig.parallel(input -> {
                    var output = JSON.MAPPER.createObjectNode();
                    output.put("txHash", "0xmint_hash");
                    output.put("blockNumber", 12345);
                    return EvalResult.complete();
                }),
                "prepare-transfer", DirectedActionConfig.parallel(input -> {
                    var output = JSON.MAPPER.createObjectNode();
                    output.put("encodedTx", "0xtransfer_calldata");
                    output.put("from", input.from());
                    output.put("to", input.to());
                    output.put("amount", input.amount());
                    return EvalResult.complete();
                }),
                "submit-transfer", DirectedActionConfig.parallel(input -> {
                    var output = JSON.MAPPER.createObjectNode();
                    output.put("txHash", "0xtransfer_hash");
                    output.put("blockNumber", 12346);
                    return EvalResult.complete();
                })
        )) {};

        var config = RuntimeConfig.builder()
                .providerName("provider1")
                .url(URI.create(wfe.wsUrl()))
                .build();

        client = new WFEWebSocketClient(config, HandlerSetFor.of(handler));
        client.connect();
        Thread.sleep(1000);

        String yaml = new String(
                getClass().getResourceAsStream("/workflows/erc20-token.yaml").readAllBytes(),
                StandardCharsets.UTF_8);
        var workflow = wfe.deployWorkflow("erc20-token", yaml);
        workflowId = workflow.path("id").asText();
    }

    @AfterAll
    void teardown() {
        if (client != null) client.close();
        if (wfe != null) wfe.close();
    }

    @Test
    void deployFlow() throws Exception {
        var input = JSON.MAPPER.createObjectNode();
        input.put("tokenName", "TestToken");
        input.put("tokenSymbol", "TST");
        input.put("initialSupply", 1000000);

        var txn = wfe.submitTransaction(workflowId, "deploy", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("success", result.path("status").asText());
        assertEquals("deployed", result.path("stage").asText());
    }

    @Test
    void mintFlow() throws Exception {
        var input = JSON.MAPPER.createObjectNode();
        input.put("to", "0xrecipient");
        input.put("amount", 500);

        var txn = wfe.submitTransaction(workflowId, "mint", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("success", result.path("status").asText());
        assertEquals("minted", result.path("stage").asText());
    }

    @Test
    void transferFlow() throws Exception {
        var input = JSON.MAPPER.createObjectNode();
        input.put("from", "0xsender");
        input.put("to", "0xreceiver");
        input.put("amount", 100);

        var txn = wfe.submitTransaction(workflowId, "transfer", input);
        String txnId = txn.path("id").asText();

        var result = wfe.waitForTransaction(txnId);

        assertEquals("success", result.path("status").asText());
        assertEquals("transferred", result.path("stage").asText());
    }
}
