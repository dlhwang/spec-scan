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
    
    // Set UI State for Loading
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

  // Event listeners for filtering
  operationSearch.addEventListener("input", filterAndRender);
  methodFilter.addEventListener("change", filterAndRender);

  function renderScanResult(result) {
    const ops = result.operations || [];
    allOperations = ops;
    rawJsonContent.textContent = JSON.stringify(result, null, 2);

    // Calculate metrics
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

    // Build unique method list for dropdown filter
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
              <button class="tab-btn active" onclick="window.switchOpTab(event, ${idx}, 'preconditions')">Preconditions (${numPre})</button>
              <button class="tab-btn" onclick="window.switchOpTab(event, ${idx}, 'assertions')">Assertions (${numAssert})</button>
              <button class="tab-btn" onclick="window.switchOpTab(event, ${idx}, 'excluded')">Excluded Rules (${numExcluded})</button>
              <button class="tab-btn" onclick="window.switchOpTab(event, ${idx}, 'payload')">Payload Schemas</button>
            </div>

            <!-- Tab: Preconditions -->
            <div class="tab-content active" id="tab-preconditions-${idx}">
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

            <!-- Tab: Payload Schemas -->
            <div class="tab-content" id="tab-payload-${idx}">
              <div class="payloads-grid">
                <div class="payload-box">
                  <div class="payload-title">Request Body Schema</div>
                  <pre><code>${op.request && op.request.bodySchema ? escapeHtml(JSON.stringify(op.request.bodySchema, null, 2)) : "No request body required."}</code></pre>
                </div>
                <div class="payload-box">
                  <div class="payload-title">Request Body Example</div>
                  <pre><code>${op.request && op.request.bodyExample ? escapeHtml(JSON.stringify(op.request.bodyExample, null, 2)) : "N/A"}</code></pre>
                </div>
                <div class="payload-box">
                  <div class="payload-title">Response 200 Schema</div>
                  <pre><code>${op.response200 && op.response200.schema ? escapeHtml(JSON.stringify(op.response200.schema, null, 2)) : "No response body."}</code></pre>
                </div>
                <div class="payload-box">
                  <div class="payload-title">Response 200 Example</div>
                  <pre><code>${op.response200 && op.response200.example ? escapeHtml(JSON.stringify(op.response200.example, null, 2)) : "N/A"}</code></pre>
                </div>
              </div>
            </div>
          </div>
        </div>
      `;
    }).join("");
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

  // Global window functions for event handlers inside dynamically generated HTML
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
    event.stopPropagation(); // Prevent accordion from toggling when clicking tabs
    const card = document.getElementById(`op-card-${index}`);
    
    // Deactivate all tab buttons and hide all contents inside this card
    card.querySelectorAll(".tab-btn").forEach(btn => btn.classList.remove("active"));
    card.querySelectorAll(".tab-content").forEach(content => content.classList.remove("active"));

    // Activate selected button and show content
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
