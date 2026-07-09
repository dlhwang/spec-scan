package io.atworks.specscan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class SpecScanWebServer {

    private static final int DEFAULT_PORT = 8088;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        int port = parsePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", SpecScanWebServer::handleIndex);
        server.createContext("/api/scan", SpecScanWebServer::handleScan);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Spec Scan Web UI: http://localhost:" + port);
    }

    private static int parsePort(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                return Integer.parseInt(arg.substring("--port=".length()));
            }
        }
        return DEFAULT_PORT;
    }

    private static void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", indexHtml());
    }

    private static void handleScan(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "Method Not Allowed"));
            return;
        }

        try {
            JsonNode request = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            String repositoryUrl = requiredText(request, "repositoryUrl");
            requiredText(request, "projectName");
            requiredText(request, "baseUrl");
            String revisionType = optionalText(request, "revisionType");
            String revision = optionalText(request, "revision");

            String result = new GitExecutionSpecScanService().scan(repositoryUrl, revisionType, revision);
            send(exchange, 200, "application/json; charset=utf-8", result);
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Scan failed");
            body.put("message", e.getMessage());
            sendJson(exchange, 500, body);
        }
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = optionalText(node, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", OBJECT_MAPPER.writeValueAsString(body));
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try {
            exchange.getResponseBody().write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            exchange.close();
        }
    }

    private static String indexHtml() {
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Spec Scan</title>
              <style>
                :root {
                  --bg: #f4f1ea;
                  --ink: #1d2528;
                  --muted: #667174;
                  --line: #c9d2cf;
                  --panel: #fffdf7;
                  --accent: #0f6f64;
                  --accent-dark: #0a4f48;
                  --danger: #a63b2f;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  font-family: "Segoe UI", Verdana, sans-serif;
                  background: linear-gradient(135deg, #f4f1ea 0%, #e5ece8 100%);
                  color: var(--ink);
                }
                header {
                  padding: 28px 32px 18px;
                  border-bottom: 1px solid var(--line);
                  background: rgba(255, 253, 247, 0.86);
                }
                h1 { margin: 0; font-size: 28px; }
                header p { margin: 8px 0 0; color: var(--muted); }
                main {
                  display: grid;
                  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
                  gap: 18px;
                  padding: 22px 32px 32px;
                }
                section {
                  background: var(--panel);
                  border: 1px solid var(--line);
                  border-radius: 8px;
                  box-shadow: 0 12px 28px rgba(29, 37, 40, 0.08);
                }
                form { padding: 18px; }
                label {
                  display: block;
                  margin: 0 0 14px;
                  font-size: 13px;
                  font-weight: 700;
                  color: var(--ink);
                }
                input, select {
                  width: 100%;
                  margin-top: 6px;
                  padding: 10px 11px;
                  border: 1px solid var(--line);
                  border-radius: 6px;
                  background: #ffffff;
                  color: var(--ink);
                  font-size: 14px;
                }
                .grid-2 {
                  display: grid;
                  grid-template-columns: 1fr 1fr;
                  gap: 12px;
                }
                button {
                  width: 100%;
                  padding: 12px 14px;
                  border: 0;
                  border-radius: 6px;
                  background: var(--accent);
                  color: white;
                  font-weight: 800;
                  cursor: pointer;
                }
                button:disabled { background: #8aa6a1; cursor: wait; }
                .status {
                  padding: 12px 18px 18px;
                  color: var(--muted);
                  font-size: 13px;
                  border-top: 1px solid var(--line);
                }
                .result { min-width: 0; overflow: hidden; }
                .toolbar {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  padding: 14px 16px;
                  border-bottom: 1px solid var(--line);
                }
                .summary {
                  display: grid;
                  grid-template-columns: repeat(3, minmax(0, 1fr));
                  gap: 10px;
                  padding: 14px 16px 0;
                }
                .metric {
                  border: 1px solid var(--line);
                  border-radius: 6px;
                  padding: 10px;
                  background: #f9fbf7;
                }
                .metric span { display: block; color: var(--muted); font-size: 12px; }
                .metric strong { display: block; margin-top: 4px; font-size: 20px; }
                .ops {
                  padding: 14px 16px;
                  display: grid;
                  gap: 10px;
                }
                .operation-tools {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) 150px auto;
                  gap: 10px;
                  padding: 14px 16px 0;
                  align-items: end;
                }
                .operation-tools label {
                  margin: 0;
                }
                .count-badge {
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  min-width: 42px;
                  height: 36px;
                  border-radius: 999px;
                  background: #e3efeb;
                  color: var(--accent-dark);
                  font-weight: 900;
                }
                .op {
                  border: 1px solid var(--line);
                  border-radius: 6px;
                  background: white;
                  overflow: hidden;
                }
                .op summary {
                  display: grid;
                  grid-template-columns: auto 1fr auto;
                  align-items: center;
                  gap: 10px;
                  padding: 11px 12px;
                  cursor: pointer;
                  list-style: none;
                }
                .op summary::-webkit-details-marker { display: none; }
                .op summary::before {
                  content: "+";
                  width: 22px;
                  height: 22px;
                  display: inline-grid;
                  place-items: center;
                  border-radius: 50%;
                  background: #e3efeb;
                  color: var(--accent-dark);
                  font-weight: 800;
                }
                .op[open] summary::before { content: "-"; }
                .op-title {
                  min-width: 0;
                  font-weight: 800;
                }
                .method {
                  display: inline-flex;
                  min-width: 58px;
                  justify-content: center;
                  margin-right: 8px;
                  padding: 4px 8px;
                  border-radius: 5px;
                  background: #eef3f0;
                  color: var(--accent-dark);
                  font-size: 12px;
                  font-weight: 900;
                }
                .method-get { background: #e6f1ff; color: #1d5d9f; }
                .method-post { background: #e5f6ec; color: #147443; }
                .method-put { background: #fff3d7; color: #8a5a00; }
                .method-delete { background: #ffe7e1; color: #a43b27; }
                .method-patch { background: #efe9ff; color: #6042a8; }
                .op-meta {
                  color: var(--muted);
                  font-size: 12px;
                }
                .op-badges {
                  display: flex;
                  gap: 6px;
                  flex-wrap: wrap;
                  justify-content: flex-end;
                }
                .badge {
                  border: 1px solid var(--line);
                  border-radius: 999px;
                  padding: 3px 8px;
                  background: #f7faf6;
                  color: var(--muted);
                  font-size: 12px;
                }
                .op code {
                  display: block;
                  margin-top: 5px;
                  overflow-wrap: anywhere;
                  color: var(--accent-dark);
                }
                .op-body {
                  display: grid;
                  grid-template-columns: repeat(3, minmax(0, 1fr));
                  gap: 10px;
                  padding: 0 12px 12px;
                  border-top: 1px solid var(--line);
                }
                .op-panel {
                  min-width: 0;
                  padding-top: 10px;
                }
                .op-panel h3 {
                  margin: 0 0 7px;
                  font-size: 12px;
                  color: var(--muted);
                  text-transform: uppercase;
                  letter-spacing: 0.04em;
                }
                .line-list {
                  display: grid;
                  gap: 6px;
                  max-height: 300px;
                  overflow: auto;
                  padding-right: 3px;
                }
                .line-item {
                  display: grid;
                  grid-template-columns: minmax(96px, 0.34fr) minmax(0, 1fr);
                  gap: 8px;
                  padding: 7px 8px;
                  border: 1px solid var(--line);
                  border-radius: 6px;
                  background: #fbfcf8;
                  font-size: 12px;
                }
                .line-key {
                  color: var(--muted);
                  font-weight: 800;
                  overflow-wrap: anywhere;
                }
                .line-value {
                  color: var(--ink);
                  overflow-wrap: anywhere;
                }
                pre {
                  margin: 0;
                  padding: 16px;
                  max-height: 520px;
                  overflow: auto;
                  background: #1d2528;
                  color: #e9f4ef;
                  font-size: 12px;
                  line-height: 1.5;
                }
                .error { color: var(--danger); }
                .muted-empty {
                  padding: 22px 16px;
                  color: var(--muted);
                  border: 1px dashed var(--line);
                  border-radius: 6px;
                  background: #fbfcf8;
                }
                @media (max-width: 900px) {
                  main { grid-template-columns: 1fr; padding: 16px; }
                  header { padding: 22px 16px 14px; }
                  .summary { grid-template-columns: 1fr; }
                  .op-body { grid-template-columns: 1fr; }
                  .operation-tools { grid-template-columns: 1fr; }
                }
              </style>
            </head>
            <body>
              <header>
                <h1>Spec Scan</h1>
                <p>Submit repository inputs and inspect the execution model returned by the scanner.</p>
              </header>
              <main>
                <section>
                  <form id="scanForm">
                    <label>Project name
                      <input name="projectName" value="my-project" required>
                    </label>
                    <label>Repository URL
                      <input name="repositoryUrl" value="https://github.com/spring-petclinic/spring-petclinic-microservices.git" required>
                    </label>
                    <label>Base URL
                      <input name="baseUrl" value="http://localhost:8080" required>
                    </label>
                    <div class="grid-2">
                      <label>Revision type
                        <select name="revisionType">
                          <option value="">default</option>
                          <option value="branch">branch</option>
                          <option value="tag">tag</option>
                          <option value="commit">commit</option>
                        </select>
                      </label>
                      <label>Revision
                        <input name="revision" placeholder="main">
                      </label>
                    </div>
                    <button id="submitButton" type="submit">Run scan</button>
                  </form>
                  <div id="status" class="status">Ready.</div>
                </section>
                <section class="result">
                  <div class="toolbar">
                    <strong>Returned execution model</strong>
                    <span id="resultState">No result</span>
                  </div>
                  <div id="summary" class="summary"></div>
                  <div class="operation-tools">
                    <label>Endpoint search
                      <input id="operationSearch" placeholder="path, operationId, controller" type="search">
                    </label>
                    <label>Method
                      <select id="methodFilter">
                        <option value="">All</option>
                      </select>
                    </label>
                    <span id="operationCount" class="count-badge">0</span>
                  </div>
                  <div id="operations" class="ops"></div>
                  <pre id="raw">{}</pre>
                </section>
              </main>
              <script>
                const form = document.querySelector("#scanForm");
                const button = document.querySelector("#submitButton");
                const statusEl = document.querySelector("#status");
                const stateEl = document.querySelector("#resultState");
                const summaryEl = document.querySelector("#summary");
                const operationsEl = document.querySelector("#operations");
                const operationSearchEl = document.querySelector("#operationSearch");
                const methodFilterEl = document.querySelector("#methodFilter");
                const operationCountEl = document.querySelector("#operationCount");
                const rawEl = document.querySelector("#raw");
                let currentOperations = [];

                form.addEventListener("submit", async (event) => {
                  event.preventDefault();
                  button.disabled = true;
                  statusEl.textContent = "Scanning repository. This can take a while for larger projects.";
                  stateEl.textContent = "Running";
                  summaryEl.innerHTML = "";
                  operationsEl.innerHTML = "";
                  operationCountEl.textContent = "0";
                  rawEl.textContent = "{}";

                  const data = Object.fromEntries(new FormData(form).entries());
                  try {
                    const response = await fetch("/api/scan", {
                      method: "POST",
                      headers: { "Content-Type": "application/json" },
                      body: JSON.stringify(data)
                    });
                    const result = await response.json();
                    if (!response.ok) {
                      throw new Error(result.message || result.error || "Scan failed");
                    }
                    renderResult(result);
                    statusEl.textContent = "Scan completed.";
                    stateEl.textContent = "Ready";
                  } catch (error) {
                    statusEl.innerHTML = `<span class="error">${escapeHtml(error.message)}</span>`;
                    stateEl.textContent = "Failed";
                  } finally {
                    button.disabled = false;
                  }
                });

                operationSearchEl.addEventListener("input", renderOperations);
                methodFilterEl.addEventListener("change", renderOperations);

                function renderResult(result) {
                  const ops = result.operations || [];
                  currentOperations = ops;
                  const withBody = ops.filter((op) => op.request && op.request.bodySchema).length;
                  const withResponse = ops.filter((op) => op.response200 && op.response200.schema).length;
                  summaryEl.innerHTML = `
                    <div class="metric"><span>Operations</span><strong>${ops.length}</strong></div>
                    <div class="metric"><span>Request bodies</span><strong>${withBody}</strong></div>
                    <div class="metric"><span>200 schemas</span><strong>${withResponse}</strong></div>
                  `;
                  renderMethodFilter(ops);
                  renderOperations();
                  rawEl.textContent = JSON.stringify(result, null, 2);
                }

                function renderMethodFilter(ops) {
                  const selected = methodFilterEl.value;
                  const methods = [...new Set(ops.map((op) => op.method).filter(Boolean))].sort();
                  methodFilterEl.innerHTML = `<option value="">All</option>${methods.map((method) => `
                    <option value="${escapeHtml(method)}">${escapeHtml(method)}</option>
                  `).join("")}`;
                  methodFilterEl.value = methods.includes(selected) ? selected : "";
                }

                function renderOperations() {
                  const term = operationSearchEl.value.trim().toLowerCase();
                  const method = methodFilterEl.value;
                  const filtered = currentOperations.filter((op) => {
                    const haystack = [
                      op.path,
                      op.operationId,
                      op.controllerClass,
                      op.method
                    ].filter(Boolean).join(" ").toLowerCase();
                    return (!term || haystack.includes(term)) && (!method || op.method === method);
                  });
                  operationCountEl.textContent = String(filtered.length);
                  if (filtered.length === 0) {
                    operationsEl.innerHTML = `<div class="muted-empty">No matching operations.</div>`;
                    return;
                  }
                  operationsEl.innerHTML = filtered.map((op, index) => `
                    <details class="op" ${index === 0 ? "open" : ""}>
                      <summary>
                        <div class="op-title">
                          <span class="method method-${escapeHtml((op.method || "").toLowerCase())}">${escapeHtml(op.method || "")}</span>
                          ${escapeHtml(op.operationId || "")}
                          <code>${escapeHtml(op.path || "")}</code>
                        </div>
                        <span class="op-meta">${escapeHtml(op.controllerClass || "")}</span>
                        <div class="op-badges">
                          <span class="badge">params ${countParams(op.request)}</span>
                          <span class="badge">validations ${(op.validationConditions || []).length}</span>
                          <span class="badge">${op.response200 && op.response200.schema ? "200 schema" : "no body"}</span>
                        </div>
                      </summary>
                      <div class="op-body">
                        <div class="op-panel">
                          <h3>Request</h3>
                          ${renderRequestLines(op.request)}
                        </div>
                        <div class="op-panel">
                          <h3>Response 200</h3>
                          ${renderResponseLines(op.response200)}
                        </div>
                        <div class="op-panel">
                          <h3>Validations</h3>
                          ${renderValidationLines(op.validationConditions || [])}
                        </div>
                      </div>
                    </details>
                  `).join("");
                }

                function countParams(request) {
                  if (!request) {
                    return 0;
                  }
                  return (request.pathParams || []).length
                    + (request.queryParams || []).length
                    + (request.headers || []).length;
                }

                function renderRequestLines(request) {
                  if (!request) {
                    return renderLines([{ key: "request", value: "none" }]);
                  }
                  const lines = [
                    { key: "contentType", value: request.contentType || "none" },
                    ...renderParamLines("path", request.pathParams || []),
                    ...renderParamLines("query", request.queryParams || []),
                    ...renderParamLines("header", request.headers || [])
                  ];
                  if (request.bodySchema) {
                    lines.push(...schemaLines("body", request.bodySchema));
                  } else {
                    lines.push({ key: "body", value: "none" });
                  }
                  if (request.bodyExample) {
                    lines.push({ key: "bodyExample", value: compactValue(request.bodyExample) });
                  }
                  return renderLines(lines);
                }

                function renderResponseLines(response) {
                  if (!response) {
                    return renderLines([{ key: "response", value: "none" }]);
                  }
                  const lines = [
                    { key: "contentType", value: response.contentType || "none" }
                  ];
                  if (response.schema) {
                    lines.push(...schemaLines("schema", response.schema));
                  } else {
                    lines.push({ key: "schema", value: "none" });
                  }
                  if (response.example) {
                    lines.push({ key: "example", value: compactValue(response.example) });
                  }
                  return renderLines(lines);
                }

                function renderValidationLines(validations) {
                  if (!validations.length) {
                    return renderLines([{ key: "validation", value: "none" }]);
                  }
                  return renderLines(validations.map((validation, index) => ({
                    key: `${index + 1}. ${validation.targetPath || ""}`,
                    value: [
                      validation.targetLocation,
                      validation.operator,
                      validation.expected == null ? null : `expected=${validation.expected}`,
                      validation.source
                    ].filter(Boolean).join(" | ")
                  })));
                }

                function renderParamLines(prefix, params) {
                  if (!params.length) {
                    return [{ key: `${prefix}Params`, value: "none" }];
                  }
                  return params.map((param) => ({
                    key: `${prefix}.${param.name || ""}`,
                    value: [
                      param.required ? "required" : "optional",
                      schemaSummary(param.schema),
                      param.example ? `example=${param.example}` : null,
                      param.defaultValue ? `default=${param.defaultValue}` : null
                    ].filter(Boolean).join(" | ")
                  }));
                }

                function schemaLines(prefix, schema) {
                  if (!schema) {
                    return [{ key: prefix, value: "none" }];
                  }
                  if (schema.type === "object" && schema.properties) {
                    const required = new Set(schema.required || []);
                    return Object.entries(schema.properties).flatMap(([name, property]) => {
                      const key = `${prefix}.${name}`;
                      const value = [
                        schemaSummary(property),
                        required.has(name) ? "required" : null
                      ].filter(Boolean).join(" | ");
                      const nested = property && property.type === "object" && property.properties
                        ? schemaLines(key, property)
                        : [];
                      return [{ key, value }, ...nested];
                    });
                  }
                  if (schema.type === "array") {
                    return [
                      { key: prefix, value: `array<${schemaSummary(schema.items || {})}>` },
                      ...schemaLines(`${prefix}[]`, schema.items)
                    ];
                  }
                  return [{ key: prefix, value: schemaSummary(schema) }];
                }

                function schemaSummary(schema) {
                  if (!schema) {
                    return "unknown";
                  }
                  const parts = [schema.type || "object"];
                  if (schema.format) parts.push(`format=${schema.format}`);
                  if (schema.minLength != null) parts.push(`minLength=${schema.minLength}`);
                  if (schema.maxLength != null) parts.push(`maxLength=${schema.maxLength}`);
                  if (schema.minimum != null) parts.push(`minimum=${schema.minimum}`);
                  if (schema.pattern) parts.push(`pattern=${schema.pattern}`);
                  if (schema.enum) parts.push(`enum=${compactValue(schema.enum)}`);
                  return parts.join(" | ");
                }

                function renderLines(lines) {
                  return `
                    <div class="line-list">
                      ${lines.map((line) => `
                        <div class="line-item">
                          <span class="line-key">${escapeHtml(line.key)}</span>
                          <span class="line-value">${escapeHtml(line.value)}</span>
                        </div>
                      `).join("")}
                    </div>
                  `;
                }

                function compactValue(value) {
                  if (value == null) {
                    return "null";
                  }
                  if (typeof value === "string") {
                    return value || '""';
                  }
                  return JSON.stringify(value);
                }

                function escapeHtml(value) {
                  return String(value).replace(/[&<>"']/g, (ch) => ({
                    "&": "&amp;",
                    "<": "&lt;",
                    ">": "&gt;",
                    '"': "&quot;",
                    "'": "&#39;"
                  }[ch]));
                }
              </script>
            </body>
            </html>
            """;
    }
}
