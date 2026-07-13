document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("scanForm");
  const button = document.getElementById("submitButton");
  const consoleText = document.getElementById("consoleText");
  const statsGrid = document.getElementById("statsGrid");
  const operationsList = document.getElementById("operationsList");
  const operationSearch = document.getElementById("operationSearch");
  const methodFilter = document.getElementById("methodFilter");
  const operationCount = document.getElementById("operationCount");
  const rawJsonContent = document.getElementById("rawJsonContent");

  // Metrics
  const metricOps = document.getElementById("metricOps");
  const metricPreconditions = document.getElementById("metricPreconditions");
  const metricAssertions = document.getElementById("metricAssertions");
  const metricExcluded = document.getElementById("metricExcluded");

  let allOperations = [];

  // Form submission: Scan Trigger
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    button.disabled = true;
    
    consoleText.textContent = "> Initializing scan payload...\n> Contacting Spec Scanner Engine...\n> This process analyzes repository Java AST and build patterns. Please wait.";
    consoleText.className = "console-text";
    statsGrid.style.display = "none";
    operationsList.innerHTML = `<div class="empty-state"><i class="ph ph-circle-notch animate-spin"></i><p>Scanning codebase and evaluating contracts. This may take 10-30 seconds depending on size...</p></div>`;
    rawJsonContent.textContent = "{}";

    const payload = Object.fromEntries(new FormData(form).entries());

    try {
      const response = await fetch("/api/scan", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      const result = await response.json();

      if (!response.ok) {
        throw new Error(result.message || result.error || "Scan failed");
      }

      consoleText.textContent = `> Scan completed successfully.\n> Analyzed ${result.operations ? result.operations.length : 0} endpoints.\n> Saved output to execution model successfully.`;
      renderScanResult(result);
    } catch (err) {
      consoleText.textContent = `> [ERROR] Scan failed:\n> ${err.message}`;
      consoleText.className = "console-text error";
      operationsList.innerHTML = `
        <div class="empty-state">
          <i class="ph ph-warning-octagon" style="color: var(--accent-red)"></i>
          <p style="color: var(--accent-red); font-weight: 600;">Scan Failed</p>
          <p>${escapeHtml(err.message)}</p>
        </div>
      `;
    } finally {
      button.disabled = false;
    }
  });

  operationSearch.addEventListener("input", filterAndRender);
  methodFilter.addEventListener("change", filterAndRender);

  function renderScanResult(result) {
    const ops = result.operations || [];
    allOperations = ops;
    rawJsonContent.textContent = JSON.stringify(result, null, 2);

    let totalPreconditions = 0;
    let totalAssertions = 0;
    let totalExcluded = 0;

    ops.forEach(op => {
      totalPreconditions += (op.requestPreconditions || []).length;
      totalAssertions += (op.responseAssertions || []).length;
      totalExcluded += (op.excludedBusinessRules || []).length;
    });

    metricOps.textContent = ops.length;
    metricPreconditions.textContent = totalPreconditions;
    metricAssertions.textContent = totalAssertions;
    metricExcluded.textContent = totalExcluded;

    statsGrid.style.display = "grid";

    const uniqueMethods = [...new Set(ops.map(op => op.method).filter(Boolean))].sort();
    methodFilter.innerHTML = `<option value="">All HTTP Methods</option>` + uniqueMethods.map(m => `
      <option value="${m}">${m}</option>
    `).join("");

    filterAndRender();
  }

  function filterAndRender() {
    const term = operationSearch.value.trim().toLowerCase();
    const selectedMethod = methodFilter.value;

    const filtered = allOperations.filter(op => {
      const targetStr = `${op.path} ${op.operationId} ${op.controllerClass} ${op.method}`.toLowerCase();
      const matchesSearch = !term || targetStr.includes(term);
      const matchesMethod = !selectedMethod || op.method === selectedMethod;
      return matchesSearch && matchesMethod;
    });

    operationCount.textContent = filtered.length;

    if (filtered.length === 0) {
      operationsList.innerHTML = `<div class="empty-state"><i class="ph ph-magnifying-glass"></i><p>No matching endpoints found.</p></div>`;
      return;
    }

    operationsList.innerHTML = filtered.map((op, idx) => {
      const methodLower = (op.method || "get").toLowerCase();
      const numPre = (op.requestPreconditions || []).length;
      const numAssert = (op.responseAssertions || []).length;
      const numExcluded = (op.excludedBusinessRules || []).length;

      return `
        <div class="op-accordion" id="op-card-${idx}">
          <div class="op-summary" onclick="window.toggleOpAccordion(${idx})">
            <div class="op-summary-left">
              <i class="ph-bold ph-caret-right chevron-icon"></i>
              <span class="badge-method ${methodLower}">${escapeHtml(op.method)}</span>
              <div class="op-identifier">
                <span class="op-id">${escapeHtml(op.operationId || "anonymous")}</span>
                <span class="op-path">${escapeHtml(op.path)}</span>
              </div>
            </div>
            <div class="op-summary-right">
              <span class="controller-info" title="${escapeHtml(op.controllerClass)}">${escapeHtml(op.controllerClass.split('.').pop())}</span>
              <div class="chips-container">
                <div class="chip chip-cyan">Pre <span>${numPre}</span></div>
                <div class="chip chip-purple">Assert <span>${numAssert}</span></div>
                <div class="chip chip-orange">Excl <span>${numExcluded}</span></div>
              </div>
            </div>
          </div>
          <div class="op-details-body" style="display: none;">
            <!-- Tabs Navigation -->
            <div class="tab-navigation">
              <button class="tab-btn active" onclick="window.switchOpTab(event, ${idx}, 'specs')">API Specs</button>
              <button class="tab-btn" onclick="window.switchOpTab(event, ${idx}, 'payload')">Payloads</button>
              <button class="tab-btn" onclick="window.switchOpTab(event, ${idx}, 'preconditions')">Preconditions (${numPre})</button>
              <button class="tab-btn" onclick="window.switchOpTab(event, ${idx}, 'assertions')">Assertions (${numAssert})</button>
              <button class="tab-btn" onclick="window.switchOpTab(event, ${idx}, 'excluded')">Excluded Rules (${numExcluded})</button>
            </div>

            <!-- Tab: API Specs (Headers, PathParams, QueryParams) -->
            <div class="tab-content active" id="tab-specs-${idx}">
              ${renderParametersTable(op.request)}
            </div>

            <!-- Tab: Payloads (Request Body Schema/Example, Response 200 Schema/Example) -->
            <div class="tab-content" id="tab-payload-${idx}">
              <div class="payloads-grid">
                <!-- Request Payload Structure -->
                <div class="payload-box">
                  <div class="payload-title">Request Body Structure</div>
                  <div style="padding: 12px; max-height: 240px; overflow-y: auto;">
                    ${op.request && op.request.bodySchema ? renderBodyFieldsTable(op.request.bodySchema, "Request") : `<p style="color: var(--text-dark); font-size:12px;">No request body required.</p>`}
                  </div>
                </div>

                <!-- Request Payload Example -->
                <div class="payload-box">
                  <div class="payload-title">Request Body Example</div>
                  <pre><code>${op.request && op.request.bodyExample ? escapeHtml(JSON.stringify(op.request.bodyExample, null, 2)) : "N/A"}</code></pre>
                </div>

                <!-- Response Payload Structure -->
                <div class="payload-box">
                  <div class="payload-title">Response 200 Structure</div>
                  <div style="padding: 12px; max-height: 240px; overflow-y: auto;">
                    ${op.response200 && op.response200.schema ? renderBodyFieldsTable(op.response200.schema, "Response") : `<p style="color: var(--text-dark); font-size:12px;">No response body.</p>`}
                  </div>
                </div>

                <!-- Response Payload Example -->
                <div class="payload-box">
                  <div class="payload-title">Response 200 Example</div>
                  <pre><code>${op.response200 && op.response200.example ? escapeHtml(JSON.stringify(op.response200.example, null, 2)) : "N/A"}</code></pre>
                </div>
              </div>
            </div>

            <!-- Tab: Preconditions -->
            <div class="tab-content" id="tab-preconditions-${idx}">
              ${renderConditionsTable(op.requestPreconditions, "No preconditions mapped.")}
            </div>

            <!-- Tab: Assertions -->
            <div class="tab-content" id="tab-assertions-${idx}">
              ${renderConditionsTable(op.responseAssertions, "No response assertions mapped.")}
            </div>

            <!-- Tab: Excluded Rules -->
            <div class="tab-content" id="tab-excluded-${idx}">
              ${renderConditionsTable(op.excludedBusinessRules, "No internal business rules were excluded.")}
            </div>
          </div>
        </div>
      `;
    }).join("");
  }

  // Renders Path variable, Query parameters, and Headers in a clean table
  function renderParametersTable(request) {
    if (!request) {
      return `<div class="empty-state" style="padding: 20px;"><i class="ph ph-info"></i><p>No request metadata</p></div>`;
    }
    const pathParams = request.pathParams || [];
    const queryParams = request.queryParams || [];
    const headers = request.headers || [];

    if (pathParams.length === 0 && queryParams.length === 0 && headers.length === 0) {
      return `<div class="empty-state" style="padding: 20px;"><i class="ph ph-info"></i><p>No Path, Query, or Header parameters required.</p></div>`;
    }

    let rows = "";
    pathParams.forEach(p => {
      rows += renderParamRow("Path Variable", p);
    });
    queryParams.forEach(p => {
      rows += renderParamRow("Query Parameter", p);
    });
    headers.forEach(p => {
      rows += renderParamRow("Header", p);
    });

    return `
      <table class="conditions-table">
        <thead>
          <tr>
            <th>Type</th>
            <th>Name</th>
            <th>Required</th>
            <th>DataType</th>
            <th>Default Value</th>
            <th>Example</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          ${rows}
        </tbody>
      </table>
    `;
  }

  function renderParamRow(type, param) {
    const typeClass = type.replace(/\s+/g, "-").toLowerCase();
    const schemaSummary = param.schema ? (param.schema.type + (param.schema.format ? ` (${param.schema.format})` : "")) : "string";
    return `
      <tr>
        <td><span class="table-location param-badge-${typeClass}">${escapeHtml(type)}</span></td>
        <td><span class="table-path">${escapeHtml(param.name)}</span></td>
        <td><span class="badge-required ${param.required ? 'req' : 'opt'}">${param.required ? 'Required' : 'Optional'}</span></td>
        <td><span class="table-operator">${escapeHtml(schemaSummary)}</span></td>
        <td><span style="color: var(--text-dark); font-family: monospace;">${escapeHtml(param.defaultValue || "-")}</span></td>
        <td><span class="table-expected">${escapeHtml(param.example || "-")}</span></td>
        <td><span style="color: var(--text-muted); font-size: 11px;">${escapeHtml(param.description || "-")}</span></td>
      </tr>
    `;
  }

  // Generates flat list table of fields inside request/response JSON schema
  function renderBodyFieldsTable(schema, title) {
    if (!schema) return "";
    const rows = [];
    buildSchemaRows(schema, "$", true, rows);

    if (rows.length === 0) {
      return `<p style="color: var(--text-dark); font-size:12px;">Empty schema object.</p>`;
    }

    return `
      <table class="conditions-table">
        <thead>
          <tr>
            <th>Field Path</th>
            <th>Type</th>
            <th>Required</th>
            <th>Constraints</th>
          </tr>
        </thead>
        <tbody>
          ${rows.map(r => `
            <tr>
              <td><span class="table-path">${escapeHtml(r.path)}</span></td>
              <td><span class="table-operator">${escapeHtml(r.type)}</span></td>
              <td><span class="badge-required ${r.required ? 'req' : 'opt'}">${r.required ? 'Required' : 'Optional'}</span></td>
              <td><span style="color: var(--text-muted); font-size: 11px; white-space: pre-wrap;">${escapeHtml(r.constraints)}</span></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;
  }

  function buildSchemaRows(schema, currentPath, isRequired, rows) {
    if (!schema) return;
    const type = schema.type || "object";

    if (type === "object" && schema.properties) {
      const requiredSet = new Set(schema.required || []);
      Object.entries(schema.properties).forEach(([key, prop]) => {
        const nextPath = currentPath === "$" ? `$.${key}` : `${currentPath}.${key}`;
        const isFieldReq = requiredSet.has(key);
        
        let constraints = [];
        if (prop.format) constraints.push(`format: ${prop.format}`);
        if (prop.minLength != null) constraints.push(`minLength: ${prop.minLength}`);
        if (prop.maxLength != null) constraints.push(`maxLength: ${prop.maxLength}`);
        if (prop.minimum != null) constraints.push(`minimum: ${prop.minimum}`);
        if (prop.pattern) constraints.push(`pattern: ${prop.pattern}`);
        if (prop.enum && prop.enum.length > 0) constraints.push(`enum: [${prop.enum.join(", ")}]`);

        rows.push({
          path: nextPath,
          type: prop.type || "object",
          required: isFieldReq,
          constraints: constraints.join(", ") || "-"
        });

        if (prop.type === "object" || (prop.type === "array" && prop.items && prop.items.type === "object")) {
          const subSchema = prop.type === "array" ? prop.items : prop;
          const subPath = prop.type === "array" ? `${nextPath}[*]` : nextPath;
          buildSchemaRows(subSchema, subPath, isFieldReq, rows);
        }
      });
    } else if (type === "array" && schema.items) {
      rows.push({
        path: `${currentPath}[*]`,
        type: schema.items.type || "object",
        required: isRequired,
        constraints: "-"
      });
      buildSchemaRows(schema.items, `${currentPath}[*]`, isRequired, rows);
    }
  }

  function renderConditionsTable(conditions, emptyMsg) {
    if (!conditions || conditions.length === 0) {
      return `<div class="empty-state" style="padding: 30px;"><i class="ph ph-info"></i><p>${emptyMsg}</p></div>`;
    }
    return `
      <table class="conditions-table">
        <thead>
          <tr>
            <th>Location</th>
            <th>Target Path</th>
            <th>Operator</th>
            <th>Expected</th>
            <th>Source</th>
          </tr>
        </thead>
        <tbody>
          ${conditions.map(cond => `
            <tr>
              <td><span class="table-location">${escapeHtml(cond.targetLocation || "BODY")}</span></td>
              <td><span class="table-path">${escapeHtml(cond.targetPath || "$")}</span></td>
              <td><span class="table-operator">${escapeHtml(cond.operator)}</span></td>
              <td><span class="table-expected">${escapeHtml(cond.expected !== null ? cond.expected : "null")}</span></td>
              <td><span style="color: var(--text-dark); font-size: 11px;">${escapeHtml(cond.source || "UNKNOWN")}</span></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;
  }

  window.toggleOpAccordion = (index) => {
    const card = document.getElementById(`op-card-${index}`);
    const body = card.querySelector(".op-details-body");
    const isExpanded = card.classList.contains("expanded");

    if (isExpanded) {
      card.classList.remove("expanded");
      body.style.display = "none";
    } else {
      card.classList.add("expanded");
      body.style.display = "flex";
    }
  };

  window.switchOpTab = (event, index, tabName) => {
    event.stopPropagation();
    const card = document.getElementById(`op-card-${index}`);
    
    card.querySelectorAll(".tab-btn").forEach(btn => btn.classList.remove("active"));
    card.querySelectorAll(".tab-content").forEach(content => content.classList.remove("active"));

    event.target.classList.add("active");
    card.querySelector(`#tab-${tabName}-${index}`).classList.add("active");
  };

  function escapeHtml(value) {
    if (value == null) return "";
    return String(value).replace(/[&<>"']/g, (ch) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;"
    }[ch]));
  }
});
