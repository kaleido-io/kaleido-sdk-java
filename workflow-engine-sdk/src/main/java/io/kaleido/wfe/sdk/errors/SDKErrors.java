// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.errors;

/**
 * SDK error codes and factory methods.
 *
 * <table>
 * <caption>Error code reference</caption>
 * <tr><th>Code</th><th>Name</th><th>When it fires</th><th>Recovery</th></tr>
 * <tr><td>KA150001</td><td>ACCOUNT_NOT_SET</td><td>ACCOUNT env var missing (REST client)</td><td>Set the ACCOUNT environment variable</td></tr>
 * <tr><td>KA150002</td><td>ENVIRONMENT_NOT_SET</td><td>ENVIRONMENT env var missing (REST client)</td><td>Set the ENVIRONMENT environment variable</td></tr>
 * <tr><td>KA150003</td><td>WORKFLOW_ENGINE_NOT_SET</td><td>WORKFLOW_ENGINE env var missing (REST client)</td><td>Set the WORKFLOW_ENGINE environment variable</td></tr>
 * <tr><td>KA150004</td><td>CONFIG_FILE_NOT_SET</td><td>WFE_CONFIG_FILE env var not set</td><td>Set WFE_CONFIG_FILE or use Spring properties</td></tr>
 * <tr><td>KA150005</td><td>CONFIG_FILE_READ_FAILED</td><td>YAML config cannot be parsed</td><td>Check config file syntax and required fields</td></tr>
 * <tr><td>KA150010</td><td>WS_CONNECT_FAILED</td><td>WebSocket connection failed</td><td>Check url in config; verify engine is reachable</td></tr>
 * <tr><td>KA150011</td><td>WS_SEND_FAILED</td><td>WebSocket send failed</td><td>Check connection state; the SDK will reconnect automatically</td></tr>
 * <tr><td>KA150012</td><td>WS_PROTOCOL_ERROR</td><td>Protocol-level error from the engine</td><td>Check engine logs for details</td></tr>
 * <tr><td>KA150020</td><td>HANDLER_NOT_FOUND</td><td>No handler registered for the requested name</td><td>Register the handler before connecting</td></tr>
 * <tr><td>KA150021</td><td>HANDLER_FAILED</td><td>Handler threw an exception</td><td>Fix the handler implementation</td></tr>
 * <tr><td>KA150030</td><td>ENGINE_API_FAILED</td><td>Engine API call failed</td><td>Check authRef and transaction payload</td></tr>
 * <tr><td>KA150040</td><td>REST_REQUEST_FAILED</td><td>REST API call returned an error</td><td>Check URL, credentials, and request body</td></tr>
 * <tr><td>KA150050</td><td>MUTUALLY_EXCLUSIVE_CONFIG</td><td>YAML config has both {@code url} and {@code server} blocks</td><td>Pick one mode; outbound uses {@code url}, server mode uses {@code server}</td></tr>
 * <tr><td>KA150051</td><td>UPGRADE_REJECTED</td><td>WebSocket upgrade rejected by Jetty</td><td>Check the engine's expected URL/headers and the server config</td></tr>
 * <tr><td>KA150052</td><td>MTLS_VALIDATION_FAILED</td><td>Inbound client certificate rejected by requiredDNAttributes</td><td>Verify the engine's client certificate matches the configured DN attributes</td></tr>
 * <tr><td>KA150053</td><td>SERVER_BIND_FAILED</td><td>Server mode failed to bind to port</td><td>Check port availability and address config</td></tr>
 * <tr><td>KA150054</td><td>SERVER_TLS_CONFIG_INVALID</td><td>Server TLS configuration is invalid</td><td>Check cert/key/CA paths and file permissions</td></tr>
 * </table>
 */
public final class SDKErrors {
    private SDKErrors() {}

    public static final String ACCOUNT_NOT_SET = "KA150001";
    public static final String ENVIRONMENT_NOT_SET = "KA150002";
    public static final String WORKFLOW_ENGINE_NOT_SET = "KA150003";
    public static final String CONFIG_FILE_NOT_SET = "KA150004";
    public static final String CONFIG_FILE_READ_FAILED = "KA150005";
    public static final String WS_CONNECT_FAILED = "KA150010";
    public static final String WS_SEND_FAILED = "KA150011";
    public static final String WS_PROTOCOL_ERROR = "KA150012";
    public static final String HANDLER_NOT_FOUND = "KA150020";
    public static final String HANDLER_FAILED = "KA150021";
    public static final String ENGINE_API_FAILED = "KA150030";
    public static final String REST_REQUEST_FAILED = "KA150040";

    public static final String MUTUALLY_EXCLUSIVE_CONFIG = "KA150050";
    public static final String UPGRADE_REJECTED = "KA150051";
    public static final String MTLS_VALIDATION_FAILED = "KA150052";
    public static final String SERVER_BIND_FAILED = "KA150053";
    public static final String SERVER_TLS_CONFIG_INVALID = "KA150054";

    public static SDKException error(String code, String message) {
        return new SDKException(code, message);
    }

    public static SDKException error(String code, String message, Throwable cause) {
        return new SDKException(code, message, cause);
    }
}
