// QuerySleuth Interactive Plan Explorer Frontend Logic

const SAMPLES = {
  sample_seq_scan: `[
  {
    "Plan": {
      "Node Type": "Seq Scan",
      "Parallel Aware": false,
      "Async Capable": false,
      "Relation Name": "tariff",
      "Schema": "public",
      "Alias": "tariff",
      "Startup Cost": 0.00,
      "Total Cost": 3845.00,
      "Plan Rows": 120,
      "Plan Width": 64,
      "Actual Startup Time": 0.024,
      "Actual Total Time": 18.420,
      "Actual Rows": 150,
      "Actual Loops": 1,
      "Filter": "((origin_port = 'ANR'::text) AND (destination_port = 'NYC'::text))",
      "Rows Removed by Filter": 99850,
      "Shared Hit Blocks": 1845,
      "Shared Read Blocks": 500,
      "Shared Dirtied Blocks": 0,
      "Shared Written Blocks": 0,
      "Local Hit Blocks": 0,
      "Local Read Blocks": 0,
      "Temp Read Blocks": 0,
      "Temp Written Blocks": 0
    },
    "Planning Time": 0.185,
    "Triggers": [],
    "Execution Time": 18.512
  }
]`,

  sample_nested_loop: `[
  {
    "Plan": {
      "Node Type": "Nested Loop",
      "Parallel Aware": false,
      "Async Capable": false,
      "Join Type": "Inner",
      "Startup Cost": 0.29,
      "Total Cost": 12840.50,
      "Plan Rows": 1500,
      "Plan Width": 128,
      "Actual Startup Time": 0.045,
      "Actual Total Time": 240.500,
      "Actual Rows": 1200,
      "Actual Loops": 1,
      "Plans": [
        {
          "Node Type": "Seq Scan",
          "Parent Relationship": "Outer",
          "Parallel Aware": false,
          "Async Capable": false,
          "Relation Name": "shipment_records",
          "Schema": "public",
          "Alias": "s",
          "Startup Cost": 0.00,
          "Total Cost": 145.00,
          "Plan Rows": 1200,
          "Plan Width": 32,
          "Actual Startup Time": 0.012,
          "Actual Total Time": 2.100,
          "Actual Rows": 1200,
          "Actual Loops": 1
        },
        {
          "Node Type": "Index Scan",
          "Parent Relationship": "Inner",
          "Parallel Aware": false,
          "Async Capable": false,
          "Scan Direction": "Forward",
          "Index Name": "idx_tariff_id",
          "Relation Name": "tariff",
          "Schema": "public",
          "Alias": "t",
          "Startup Cost": 0.29,
          "Total Cost": 8.30,
          "Plan Rows": 1,
          "Plan Width": 96,
          "Actual Startup Time": 0.180,
          "Actual Total Time": 0.198,
          "Actual Rows": 1,
          "Actual Loops": 1200,
          "Index Cond": "(id = s.tariff_id)"
        }
      ]
    },
    "Planning Time": 0.420,
    "Triggers": [],
    "Execution Time": 241.100
  }
]`,

  sample_cardinality: `[
  {
    "Plan": {
      "Node Type": "Seq Scan",
      "Parallel Aware": false,
      "Async Capable": false,
      "Relation Name": "staging_updates",
      "Schema": "public",
      "Alias": "su",
      "Startup Cost": 0.00,
      "Total Cost": 5120.00,
      "Plan Rows": 15,
      "Plan Width": 48,
      "Actual Startup Time": 0.030,
      "Actual Total Time": 34.200,
      "Actual Rows": 45000,
      "Actual Loops": 1,
      "Filter": "(status = 'PENDING'::text)",
      "Rows Removed by Filter": 5000,
      "Shared Hit Blocks": 3120,
      "Shared Read Blocks": 0
    },
    "Planning Time": 0.120,
    "Execution Time": 35.800
  }
]`,

  sample_disk_spill: `[
  {
    "Plan": {
      "Node Type": "Sort",
      "Parallel Aware": false,
      "Async Capable": false,
      "Startup Cost": 14500.00,
      "Total Cost": 15200.00,
      "Plan Rows": 80000,
      "Plan Width": 128,
      "Actual Startup Time": 95.120,
      "Actual Total Time": 112.450,
      "Actual Rows": 80000,
      "Actual Loops": 1,
      "Sort Key": ["customer_id", "created_at DESC"],
      "Sort Method": "external merge",
      "Sort Space Used": 14520,
      "Sort Space Type": "Disk",
      "Temp Written Blocks": 1815,
      "Temp Read Blocks": 1815,
      "Plans": [
        {
          "Node Type": "Seq Scan",
          "Relation Name": "shipment_records",
          "Alias": "sr",
          "Startup Cost": 0.00,
          "Total Cost": 4200.00,
          "Plan Rows": 80000,
          "Plan Width": 128,
          "Actual Startup Time": 0.015,
          "Actual Total Time": 25.100,
          "Actual Rows": 80000,
          "Actual Loops": 1
        }
      ]
    },
    "Planning Time": 0.210,
    "Execution Time": 115.800
  }
]`,

  sample_function_scan: `Function Scan on gettariffmainsearchsinglerow_db (cost=0.25..10.25 rows=1000 width=2289) (actual time=440.580..440.737 rows=2000 loops=1)
 Planning Time: 0.133 ms
 Execution Time: 442.744 ms`
};

let currentAnalysis = null;
let selectedNode = null;
let currentSeverityFilter = 'ALL';
const nodeMap = new Map();
let bufferChartInstance = null;
let rowsChartInstance = null;
let timeChartInstance = null;

document.addEventListener('DOMContentLoaded', () => {
  initHealthCheck();
  bindEvents();
});

function initHealthCheck() {
  fetch('/api/v1/health')
    .then(res => res.json())
    .then(data => {
      const el = document.getElementById('healthStatusText');
      if (el && data.status === 'UP') {
        el.textContent = `Spring Boot: Connected (v${data.version})`;
      }
    })
    .catch(() => {
      const el = document.getElementById('healthStatusText');
      if (el) el.textContent = 'Spring Boot: Offline (Start server)';
    });
}

function bindEvents() {
  const sampleSelect = document.getElementById('sampleSelector');
  const planInput = document.getElementById('planInput');
  const btnAnalyze = document.getElementById('btnAnalyze');
  const btnClear = document.getElementById('btnClear');
  const btnFormat = document.getElementById('btnFormat');

  sampleSelect.addEventListener('change', (e) => {
    const val = e.target.value;
    if (val && SAMPLES[val]) {
      planInput.value = SAMPLES[val];
    }
  });

  btnClear.addEventListener('click', () => {
    planInput.value = '';
    sampleSelect.value = '';
    document.getElementById('resultsSection').classList.add('hidden');
  });

  btnFormat.addEventListener('click', () => {
    try {
      const parsed = JSON.parse(planInput.value);
      planInput.value = JSON.stringify(parsed, null, 2);
    } catch (e) {
      alert('Unable to format: Invalid JSON format.');
    }
  });

  btnAnalyze.addEventListener('click', () => {
    analyzeCurrentPlan();
  });

  // Tab switching
  document.querySelectorAll('.detail-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.detail-tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

      tab.classList.add('active');
      const targetId = tab.getAttribute('data-tab');
      const targetEl = document.getElementById(targetId);
      if (targetEl) targetEl.classList.add('active');

      if (targetId === 'charts-tab' && currentAnalysis) {
        resizeOrRedrawCharts();
      }
    });
  });

  // Severity filter pills
  document.querySelectorAll('.filter-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentSeverityFilter = btn.getAttribute('data-severity');
      renderFindingsList();
    });
  });

  // Tree Controls
  document.getElementById('btnExpandAll').addEventListener('click', () => {
    document.querySelectorAll('.children-container').forEach(c => c.classList.remove('hidden'));
  });
  document.getElementById('btnCollapseAll').addEventListener('click', () => {
    document.querySelectorAll('.children-container').forEach(c => c.classList.add('hidden'));
  });
}

async function analyzeCurrentPlan() {
  const planInput = document.getElementById('planInput');
  const timerEl = document.getElementById('analysisTimer');
  const rawText = planInput.value.trim();

  if (!rawText) {
    alert('Please paste a PostgreSQL JSON plan or select a preset sample.');
    return;
  }

  timerEl.textContent = 'Analyzing plan...';
  const startTime = performance.now();

  try {
    const isJson = rawText.startsWith('{') || rawText.startsWith('[');
    const contentType = isJson ? 'application/json' : 'text/plain';

    const response = await fetch('/api/v1/plans/analyze', {
      method: 'POST',
      headers: { 'Content-Type': contentType },
      body: rawText
    });

    const elapsed = Math.round(performance.now() - startTime);

    if (!response.ok) {
      const err = await response.json();
      throw new Error(err.message || 'Analysis failed');
    }

    const data = await response.json();
    currentAnalysis = data;
    timerEl.textContent = `Completed in ${elapsed} ms`;

    renderResults(data, rawText);
  } catch (err) {
    timerEl.textContent = '';
    alert('Analysis Error: ' + err.message);
  }
}

function renderResults(data, rawJson) {
  const resultsSection = document.getElementById('resultsSection');
  resultsSection.classList.remove('hidden');

  const summary = data.summary || {};
  const metrics = data.metrics || {};

  // Metrics
  const execTime = summary.executionTimeMs != null ? summary.executionTimeMs.toFixed(3) : 'N/A';
  const planTime = summary.planningTimeMs != null ? summary.planningTimeMs.toFixed(3) : 'N/A';
  document.getElementById('metricExecTime').textContent = execTime;
  document.getElementById('metricPlanningTime').textContent = `Planning: ${planTime} ms`;

  document.getElementById('metricTotalCost').textContent = summary.totalCost != null ? summary.totalCost.toLocaleString() : 'N/A';
  document.getElementById('metricNodesCount').textContent = `${summary.totalNodes || 0} nodes in tree`;

  document.getElementById('metricBufferHits').textContent = (summary.totalSharedHitBlocks || 0).toLocaleString();
  document.getElementById('metricBufferReads').textContent = `${(summary.totalSharedReadBlocks || 0).toLocaleString()} disk reads`;

  const findings = data.findings || [];
  document.getElementById('metricFindingsTotal').textContent = findings.length;
  document.getElementById('findingsTabCount').textContent = findings.length;

  // Severity Pills
  const pillsContainer = document.getElementById('findingsPills');
  pillsContainer.innerHTML = '';
  const counts = metrics.severityCounts || {};
  if (counts.HIGH) {
    pillsContainer.innerHTML += `<span class="pill pill-high">${counts.HIGH} High</span>`;
  }
  if (counts.MEDIUM) {
    pillsContainer.innerHTML += `<span class="pill pill-med">${counts.MEDIUM} Med</span>`;
  }
  if (counts.LOW) {
    pillsContainer.innerHTML += `<span class="pill pill-low">${counts.LOW} Low</span>`;
  }

  // Limitations Banner
  const limitations = data.limitations || [];
  const limitationsBanner = document.getElementById('limitationsBanner');
  const limitationsList = document.getElementById('limitationsList');
  if (limitations.length > 0) {
    limitationsList.innerHTML = limitations.map(l => `<li>${escapeHtml(l)}</li>`).join('');
    limitationsBanner.classList.remove('hidden');
  } else {
    limitationsBanner.classList.add('hidden');
  }

  // Clear Node Map and Populate Tree
  nodeMap.clear();
  const treeContainer = document.getElementById('planTreeContainer');
  treeContainer.innerHTML = '';
  if (data.planTree) {
    treeContainer.appendChild(createTreeNodeElement(data.planTree, findings));
  }

  // Findings Tab
  renderFindingsList();

  // Plain English Guide Tab
  renderPlainEnglishGuide(data);

  // Visual Charts Tab
  renderCharts(data);

  // Raw JSON View
  document.getElementById('rawJsonView').textContent = formatJsonSafe(rawJson);

  // Auto-select root node for Inspector
  if (data.planTree) {
    inspectNode(data.planTree.id);
  }
}

function createTreeNodeElement(node, findings) {
  nodeMap.set(node.id, node);

  const wrapper = document.createElement('div');
  wrapper.className = 'tree-node-wrapper';

  const card = document.createElement('div');
  card.className = 'tree-node-card';
  card.id = `tree-${node.id}`;

  // Check if any finding targets this node
  const nodeFindings = findings.filter(f => f.targetNodeId === node.id);
  if (nodeFindings.length > 0) {
    const hasHigh = nodeFindings.some(f => f.severity === 'HIGH');
    if (hasHigh) card.classList.add('highlight-finding');
  }

  const relation = node.relationName ? `<span class="relation-tag">${escapeHtml(node.relationName)}</span>` : '';
  const timeText = node.actualTotalTime != null ? `${node.actualTotalTime.toFixed(2)} ms` : `est. cost: ${node.totalCost}`;

  let loopsText = '';
  if (node.actualLoops != null && node.actualLoops > 1) {
    loopsText = `<span class="metric-chip"><strong>loops:</strong> ${node.actualLoops.toLocaleString()}</span>`;
  }

  let rowsText = '';
  if (node.actualRows != null) {
    const est = node.planRows != null ? ` (est. ${Math.round(node.planRows)})` : '';
    rowsText = `<span class="metric-chip"><strong>rows:</strong> ${node.actualRows.toLocaleString()}${est}</span>`;
  }

  let bufferText = '';
  if (node.sharedReadBlocks != null && node.sharedReadBlocks > 0) {
    bufferText = `<span class="metric-chip text-amber"><strong>disk reads:</strong> ${node.sharedReadBlocks}</span>`;
  }

  card.innerHTML = `
    <div class="node-header-row">
      <div class="node-type-badge">
        <span>${escapeHtml(node.nodeType)}</span>
        ${relation}
      </div>
      <div class="node-time-badge">${timeText}</div>
    </div>
    <div class="node-metrics-row">
      ${rowsText}
      ${loopsText}
      ${bufferText}
      <span class="metric-chip"><strong>id:</strong> ${node.id}</span>
    </div>
  `;

  card.addEventListener('click', (e) => {
    e.stopPropagation();
    inspectNode(node.id);
  });

  wrapper.appendChild(card);

  // Child Nodes
  if (node.plans && node.plans.length > 0) {
    const childrenContainer = document.createElement('div');
    childrenContainer.className = 'children-container';
    node.plans.forEach(child => {
      childrenContainer.appendChild(createTreeNodeElement(child, findings));
    });
    wrapper.appendChild(childrenContainer);
  }

  return wrapper;
}

function renderFindingsList() {
  const container = document.getElementById('findingsContainer');
  container.innerHTML = '';

  if (!currentAnalysis || !currentAnalysis.findings || currentAnalysis.findings.length === 0) {
    container.innerHTML = `<div class="inspector-empty"><p>No suspicious bottlenecks or anomalies detected.</p></div>`;
    return;
  }

  const filtered = currentAnalysis.findings.filter(f => {
    if (currentSeverityFilter === 'ALL') return true;
    return f.severity === currentSeverityFilter;
  });

  if (filtered.length === 0) {
    container.innerHTML = `<div class="inspector-empty"><p>No ${currentSeverityFilter.toLowerCase()} severity findings detected.</p></div>`;
    return;
  }

  filtered.forEach(finding => {
    const card = document.createElement('div');
    card.className = `finding-card severity-${finding.severity.toLowerCase()}`;

    const severityPill = `<span class="pill pill-${finding.severity.toLowerCase()}">${finding.severity}</span>`;
    const confidencePill = `<span class="pill" style="background:rgba(255,255,255,0.06); color:#94a3b8;">Confidence: ${finding.confidence}</span>`;

    card.innerHTML = `
      <div class="finding-header">
        <h4 class="finding-title">${escapeHtml(finding.title)}</h4>
        <div class="finding-tags">
          ${severityPill}
          ${confidencePill}
        </div>
      </div>
      <div class="finding-evidence-box">${escapeHtml(finding.evidence)}</div>
      <p class="finding-explanation">${escapeHtml(finding.explanation)}</p>
      <div class="finding-actions-box">
        <strong>Suggested Investigation / Next Steps:</strong>
        <pre>${escapeHtml(finding.suggestedNextStep)}</pre>
      </div>
      ${finding.targetNodeId ? `
        <button class="btn-locate-node" onclick="focusTreeNode('${finding.targetNodeId}')">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          Highlight Node in Tree (${finding.targetNodeId})
        </button>
      ` : ''}
    `;

    container.appendChild(card);
  });
}

function inspectNode(nodeId) {
  const node = nodeMap.get(nodeId);
  if (!node) return;

  selectedNode = node;

  // Highlight card in tree
  document.querySelectorAll('.tree-node-card').forEach(c => c.classList.remove('selected'));
  const card = document.getElementById(`tree-${nodeId}`);
  if (card) card.classList.add('selected');

  document.getElementById('inspectorPlaceholder').classList.add('hidden');
  const details = document.getElementById('inspectorDetails');
  details.classList.remove('hidden');

  let html = `
    <div class="inspector-section">
      <h4>Node Identification</h4>
      <table class="property-table">
        <tr><td>Node ID</td><td>${node.id}</td></tr>
        <tr><td>Node Type</td><td>${escapeHtml(node.nodeType)}</td></tr>
        ${node.relationName ? `<tr><td>Relation</td><td>${escapeHtml(node.relationName)}</td></tr>` : ''}
        ${node.schema ? `<tr><td>Schema</td><td>${escapeHtml(node.schema)}</td></tr>` : ''}
        ${node.alias ? `<tr><td>Alias</td><td>${escapeHtml(node.alias)}</td></tr>` : ''}
        ${node.indexName ? `<tr><td>Index Name</td><td>${escapeHtml(node.indexName)}</td></tr>` : ''}
      </table>
    </div>

    <div class="inspector-section">
      <h4>Timing &amp; Row Counts</h4>
      <table class="property-table">
        <tr><td>Actual Total Time</td><td>${node.actualTotalTime != null ? node.actualTotalTime.toFixed(3) + ' ms' : 'N/A'}</td></tr>
        <tr><td>Exclusive Time</td><td>${node.exclusiveTimeMs != null ? node.exclusiveTimeMs.toFixed(3) + ' ms' : 'N/A'}</td></tr>
        <tr><td>Actual Startup Time</td><td>${node.actualStartupTime != null ? node.actualStartupTime.toFixed(3) + ' ms' : 'N/A'}</td></tr>
        <tr><td>Actual Loops</td><td>${node.actualLoops != null ? node.actualLoops.toLocaleString() : '1'}</td></tr>
        <tr><td>Actual Rows</td><td>${node.actualRows != null ? node.actualRows.toLocaleString() : 'N/A'}</td></tr>
        <tr><td>Estimated Rows (Plan)</td><td>${node.planRows != null ? Math.round(node.planRows).toLocaleString() : 'N/A'}</td></tr>
        <tr><td>Estimated Cost</td><td>${node.startupCost != null ? node.startupCost : '0.0'} .. ${node.totalCost != null ? node.totalCost : 'N/A'}</td></tr>
      </table>
    </div>
  `;

  if (node.filter || node.rowsRemovedByFilter != null || node.indexCond) {
    html += `
      <div class="inspector-section">
        <h4>Filters &amp; Predicates</h4>
        <table class="property-table">
          ${node.filter ? `<tr><td>Filter</td><td><code>${escapeHtml(node.filter)}</code></td></tr>` : ''}
          ${node.rowsRemovedByFilter != null ? `<tr><td>Removed by Filter</td><td>${node.rowsRemovedByFilter.toLocaleString()}</td></tr>` : ''}
          ${node.indexCond ? `<tr><td>Index Condition</td><td><code>${escapeHtml(node.indexCond)}</code></td></tr>` : ''}
          ${node.hashCond ? `<tr><td>Hash Condition</td><td><code>${escapeHtml(node.hashCond)}</code></td></tr>` : ''}
        </table>
      </div>
    `;
  }

  const hasBuffers = (node.sharedHitBlocks != null && node.sharedHitBlocks > 0) ||
                     (node.sharedReadBlocks != null && node.sharedReadBlocks > 0) ||
                     (node.localHitBlocks != null && node.localHitBlocks > 0) ||
                     (node.localReadBlocks != null && node.localReadBlocks > 0) ||
                     (node.tempReadBlocks != null && node.tempReadBlocks > 0) ||
                     (node.tempWrittenBlocks != null && node.tempWrittenBlocks > 0);

  if (hasBuffers) {
    html += `
      <div class="inspector-section">
        <h4>Buffer &amp; I/O Activity</h4>
        <table class="property-table">
          ${node.sharedHitBlocks != null ? `<tr><td>Shared Hit Blocks</td><td>${node.sharedHitBlocks.toLocaleString()}</td></tr>` : ''}
          ${node.sharedReadBlocks != null ? `<tr><td>Shared Read Blocks</td><td>${node.sharedReadBlocks.toLocaleString()}</td></tr>` : ''}
          ${node.sharedWrittenBlocks != null ? `<tr><td>Shared Written Blocks</td><td>${node.sharedWrittenBlocks.toLocaleString()}</td></tr>` : ''}
          ${node.localHitBlocks != null ? `<tr><td>Local Hit Blocks</td><td>${node.localHitBlocks.toLocaleString()}</td></tr>` : ''}
          ${node.localReadBlocks != null ? `<tr><td>Local Read Blocks</td><td>${node.localReadBlocks.toLocaleString()}</td></tr>` : ''}
          ${node.localWrittenBlocks != null ? `<tr><td>Local Written Blocks</td><td>${node.localWrittenBlocks.toLocaleString()}</td></tr>` : ''}
          ${node.tempReadBlocks != null ? `<tr><td>Temp Read Blocks</td><td>${node.tempReadBlocks.toLocaleString()}</td></tr>` : ''}
          ${node.tempWrittenBlocks != null ? `<tr><td>Temp Written Blocks</td><td>${node.tempWrittenBlocks.toLocaleString()}</td></tr>` : ''}
        </table>
      </div>
    `;
  }

  if (node.functionCall) {
    html += `
      <div class="inspector-section">
        <h4>Function Call</h4>
        <div class="finding-evidence-box">${escapeHtml(node.functionCall)}</div>
      </div>
    `;
  }

  if (node.sortMethod || node.sortSpaceType) {
    html += `
      <div class="inspector-section">
        <h4>Sort Details</h4>
        <table class="property-table">
          <tr><td>Sort Method</td><td>${escapeHtml(node.sortMethod || 'N/A')}</td></tr>
          <tr><td>Space Type</td><td>${escapeHtml(node.sortSpaceType || 'N/A')}</td></tr>
          <tr><td>Space Used</td><td>${node.sortSpaceUsed != null ? node.sortSpaceUsed.toLocaleString() + ' kB' : 'N/A'}</td></tr>
        </table>
      </div>
    `;
  }

  // Unmapped Raw Properties
  if (node.rawProperties && Object.keys(node.rawProperties).length > 0) {
    html += `
      <div class="inspector-section">
        <h4>Additional Properties</h4>
        <table class="property-table">
          ${Object.entries(node.rawProperties).map(([k, v]) => `
            <tr><td>${escapeHtml(k)}</td><td>${escapeHtml(JSON.stringify(v))}</td></tr>
          `).join('')}
        </table>
      </div>
    `;
  }

  details.innerHTML = html;
}

window.focusTreeNode = function(nodeId) {
  // Switch to inspector or keep tree in view
  inspectNode(nodeId);
  const card = document.getElementById(`tree-${nodeId}`);
  if (card) {
    card.scrollIntoView({ behavior: 'smooth', block: 'center' });
    card.classList.add('selected');
  }
};

function formatJsonSafe(text) {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch (e) {
    return text;
  }
}

function escapeHtml(str) {
  if (str == null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ============================================================================
// Plain English Guide Implementation
// ============================================================================

function formatBlocksToBytes(blocks) {
  if (blocks == null || blocks <= 0) {
    return { raw: 0, kb: 0, mb: 0, gb: 0, formatted: '0 KB', shortFormatted: '0 KB' };
  }
  const kb = blocks * 8;
  const mb = kb / 1024;
  const gb = mb / 1024;
  let formatted = '';
  let shortFormatted = '';
  if (gb >= 1.0) {
    formatted = `${gb.toFixed(2)} GB (${mb.toLocaleString(undefined, {maximumFractionDigits: 1})} MB)`;
    shortFormatted = `${gb.toFixed(2)} GB`;
  } else if (mb >= 1.0) {
    formatted = `${mb.toFixed(2)} MB (${kb.toLocaleString()} KB)`;
    shortFormatted = `${mb.toFixed(2)} MB`;
  } else {
    formatted = `${kb.toLocaleString()} KB`;
    shortFormatted = `${kb.toLocaleString()} KB`;
  }
  return { raw: blocks, kb, mb, gb, formatted, shortFormatted };
}

function renderPlainEnglishGuide(data) {
  const container = document.getElementById('plainEnglishContent');
  if (!container) return;

  const summary = data.summary || {};
  const root = data.planTree || {};
  const findings = data.findings || [];

  // Metrics extraction
  const execTime = summary.executionTimeMs != null ? summary.executionTimeMs : (root.actualTotalTime || null);
  const planTime = summary.planningTimeMs != null ? summary.planningTimeMs : 0;
  const totalTime = (execTime != null ? execTime : 0) + planTime;
  const execPercent = totalTime > 0 && execTime != null ? ((execTime / totalTime) * 100).toFixed(1) : '100';
  const planPercent = totalTime > 0 && planTime != null ? ((planTime / totalTime) * 100).toFixed(1) : '0';

  const rowsProduced = summary.maxActualRows != null ? summary.maxActualRows : (root.actualRows != null ? root.actualRows : null);
  const rowsEstimated = root.planRows != null ? Math.round(root.planRows) : null;

  // Buffer metrics
  const sharedHit = summary.totalSharedHitBlocks || 0;
  const sharedRead = summary.totalSharedReadBlocks || 0;
  const localHit = summary.totalLocalHitBlocks || 0;
  const localRead = summary.totalLocalReadBlocks || 0;
  const localWritten = summary.totalLocalWrittenBlocks || 0;
  const tempRead = summary.totalTempReadBlocks || 0;
  const tempWritten = summary.totalTempWrittenBlocks || 0;

  const totalRamHitBlocks = sharedHit + localHit;
  const totalDiskReadBlocks = sharedRead + localRead + tempRead;
  const totalDiskWrites = tempWritten + localWritten + (summary.totalSharedWrittenBlocks || 0);

  const ramHitBytes = formatBlocksToBytes(totalRamHitBlocks);
  const diskReadBytes = formatBlocksToBytes(totalDiskReadBlocks);
  const diskWriteBytes = formatBlocksToBytes(totalDiskWrites);

  let cacheHitRatio = null;
  const totalCacheLookups = totalRamHitBlocks + totalDiskReadBlocks;
  if (totalCacheLookups > 0) {
    cacheHitRatio = ((totalRamHitBlocks / totalCacheLookups) * 100).toFixed(2);
  }

  // Pace & Cardinality
  let paceText = 'N/A';
  let throughputText = 'N/A';
  if (execTime != null && rowsProduced != null && rowsProduced > 0) {
    const msPerRow = (execTime / rowsProduced).toFixed(3);
    const rps = Math.round(rowsProduced / (execTime / 1000));
    paceText = `${msPerRow} ms / row`;
    throughputText = `${rps.toLocaleString()} rows / sec`;
  }

  let cardinalityDiffText = 'Accurate';
  let cardinalityMultiplier = 1.0;
  if (rowsEstimated != null && rowsProduced != null && rowsEstimated > 0) {
    if (rowsProduced >= rowsEstimated) {
      cardinalityMultiplier = (rowsProduced / rowsEstimated).toFixed(1);
      cardinalityDiffText = `${cardinalityMultiplier}x more rows than planned`;
    } else {
      cardinalityMultiplier = (rowsEstimated / rowsProduced).toFixed(1);
      cardinalityDiffText = `${cardinalityMultiplier}x fewer rows than planned`;
    }
  }

  // Health Status
  const highFindings = findings.filter(f => f.severity === 'HIGH').length;
  const medFindings = findings.filter(f => f.severity === 'MEDIUM').length;

  let healthTag = '<span class="plain-card-tag tag-emerald">Healthy Query</span>';
  if (highFindings > 0) {
    healthTag = `<span class="plain-card-tag tag-rose">Critical Attention (${highFindings} High Priority)</span>`;
  } else if (medFindings > 0) {
    healthTag = `<span class="plain-card-tag tag-amber">Review Recommended (${medFindings} Warnings)</span>`;
  }

  let html = `
    <!-- Card 1: Executive Summary -->
    <div class="plain-card">
      <div class="plain-card-header">
        <div class="plain-card-title-group">
          <span class="plain-card-icon">⚡</span>
          <h3 class="plain-card-title">In Simple Words: Query Execution Story</h3>
        </div>
        ${healthTag}
      </div>

      <div class="stat-callout-grid">
        <div class="stat-callout">
          <span class="stat-label">Total Execution Time</span>
          <span class="stat-value text-cyan">${execTime != null ? execTime.toFixed(2) + ' ms' : 'N/A'}</span>
          <span class="stat-sub">${execPercent}% executing, ${planPercent}% planning</span>
        </div>

        <div class="stat-callout">
          <span class="stat-label">Rows Produced</span>
          <span class="stat-value">${rowsProduced != null ? rowsProduced.toLocaleString() : 'N/A'}</span>
          <span class="stat-sub">${rowsEstimated != null ? 'Optimizer estimated ' + rowsEstimated.toLocaleString() : 'Estimated: N/A'}</span>
        </div>

        <div class="stat-callout">
          <span class="stat-label">Execution Pace</span>
          <span class="stat-value text-emerald">${paceText}</span>
          <span class="stat-sub">${throughputText}</span>
        </div>

        <div class="stat-callout">
          <span class="stat-label">RAM Cache Hit Ratio</span>
          <span class="stat-value ${cacheHitRatio != null && parseFloat(cacheHitRatio) >= 99 ? 'text-emerald' : 'text-amber'}">
            ${cacheHitRatio != null ? cacheHitRatio + '%' : 'N/A'}
          </span>
          <span class="stat-sub">${cacheHitRatio != null && parseFloat(cacheHitRatio) >= 99 ? 'Excellent memory caching' : 'Disk reads observed'}</span>
        </div>
      </div>

      <div class="plain-info-list">
        <div class="plain-info-item">
          <span class="item-icon">⏱️</span>
          <div>
            <strong>How long did it take?</strong>
            ${execTime != null ? `The database completed this query in <strong>${execTime.toFixed(2)} milliseconds</strong>.` : 'Actual execution time was not measured (plan run without ANALYZE).'}
            ${planTime > 0 ? `PostgreSQL spent <strong>${planTime.toFixed(3)} ms</strong> planning the execution, and <strong>${execTime != null ? execTime.toFixed(2) : '0'} ms</strong> actually running it.` : ''}
          </div>
        </div>

        <div class="plain-info-item">
          <span class="item-icon">🎯</span>
          <div>
            <strong>What was the output?</strong>
            The query delivered <strong>${rowsProduced != null ? rowsProduced.toLocaleString() : 'an unmeasured number of'} rows</strong>.
            ${paceText !== 'N/A' ? `On average, PostgreSQL spent <strong>${paceText}</strong> to locate and deliver each row.` : ''}
          </div>
        </div>
      </div>
    </div>

    <!-- Card 2: Memory & Buffers Breakdown with Exact Numbers -->
    <div class="plain-card">
      <div class="plain-card-header">
        <div class="plain-card-title-group">
          <span class="plain-card-icon">🧠</span>
          <h3 class="plain-card-title">Memory &amp; Buffer Usage (Explained with Real Numbers)</h3>
        </div>
        <span class="plain-card-tag tag-cyan">PostgreSQL Buffer Engine</span>
      </div>

      <div class="plain-info-list" style="margin-bottom: 1.25rem;">
        <div class="plain-info-item">
          <span class="item-icon">💡</span>
          <div>
            <strong>What are "Buffers" in PostgreSQL?</strong>
            PostgreSQL does not read database tables byte-by-byte. Instead, all table data and indexes are organized into fixed <strong>8 KB memory chunks called "blocks" or "pages"</strong>. Whenever PostgreSQL checks data, it loads the corresponding 8 KB page into RAM.
            <div class="code-calc">PostgreSQL Page Formula: 8,192 bytes (8 KB) &bull; Total MB = (Blocks &times; 8) &divide; 1,024</div>
          </div>
        </div>
      </div>

      <div class="stat-callout-grid">
        <div class="stat-callout">
          <span class="stat-label">RAM Memory Read (Buffer Hits)</span>
          <span class="stat-value text-emerald">${ramHitBytes.shortFormatted}</span>
          <span class="stat-sub">${totalRamHitBlocks.toLocaleString()} blocks read from RAM</span>
          <span class="stat-badge ${totalRamHitBlocks > 50000 ? 'pill-med' : 'pill-low'}">
            ${totalRamHitBlocks > 50000 ? 'High Memory Churn' : 'Normal RAM Cache'}
          </span>
        </div>

        <div class="stat-callout">
          <span class="stat-label">Physical Disk Reads</span>
          <span class="stat-value ${totalDiskReadBlocks > 0 ? 'text-amber' : 'text-emerald'}">${diskReadBytes.shortFormatted}</span>
          <span class="stat-sub">${totalDiskReadBlocks.toLocaleString()} blocks read from storage</span>
          <span class="stat-badge ${totalDiskReadBlocks > 0 ? 'pill-med' : 'pill-low'}">
            ${totalDiskReadBlocks === 0 ? 'Zero Disk I/O (100% Cached)' : 'Disk I/O Latency'}
          </span>
        </div>

        <div class="stat-callout">
          <span class="stat-label">Temporary / Local Session Buffers</span>
          <span class="stat-value text-cyan">${formatBlocksToBytes(localHit + localRead).shortFormatted}</span>
          <span class="stat-sub">${(localHit + localRead).toLocaleString()} stored procedure / temp table blocks</span>
          <span class="stat-badge ${localHit > 10000 ? 'pill-high' : 'pill-low'}">
            ${localHit > 10000 ? 'Heavy PL/pgSQL Activity' : 'Standard Session Memory'}
          </span>
        </div>

        <div class="stat-callout">
          <span class="stat-label">Spill to Disk / Temp Writes</span>
          <span class="stat-value ${totalDiskWrites > 0 ? 'text-amber' : 'text-emerald'}">${diskWriteBytes.shortFormatted}</span>
          <span class="stat-sub">${totalDiskWrites.toLocaleString()} blocks written to disk</span>
          <span class="stat-badge ${totalDiskWrites > 0 ? 'pill-high' : 'pill-low'}">
            ${totalDiskWrites > 0 ? '⚠️ Memory Spilled to Disk' : 'Zero Disk Spills'}
          </span>
        </div>
      </div>

      <h4 class="plain-section-title">What These Numbers Mean In Reality:</h4>
      <div class="plain-info-list">
        <div class="plain-info-item">
          <span class="item-icon">⚡</span>
          <div>
            <strong>1. RAM Memory Hits: ${ramHitBytes.formatted} (${totalRamHitBlocks.toLocaleString()} blocks)</strong><br>
            PostgreSQL located all of these blocks directly in fast RAM memory cache. Reading from RAM takes nanoseconds and causes zero physical disk wear.
            ${totalRamHitBlocks > 50000 && rowsProduced != null && rowsProduced > 0 ? `<br><span style="color: #fbbf24;">⚠️ However, notice that this query churned through <strong>${(totalRamHitBlocks / rowsProduced).toFixed(0)} blocks (~${((totalRamHitBlocks * 8 / 1024) / rowsProduced).toFixed(2)} MB) for every single row returned</strong>. This high memory traffic can cause CPU cache thrashing.</span>` : ''}
          </div>
        </div>

        <div class="plain-info-item">
          <span class="item-icon">💾</span>
          <div>
            <strong>2. Physical Disk Reads: ${diskReadBytes.formatted} (${totalDiskReadBlocks.toLocaleString()} blocks)</strong><br>
            ${totalDiskReadBlocks === 0 
              ? 'Awesome! PostgreSQL did not have to read from the hard drive or SSD. All required data was warm in RAM cache.' 
              : `PostgreSQL had to fetch <strong>${totalDiskReadBlocks.toLocaleString()} blocks</strong> directly from storage. Physical disk reads are orders of magnitude slower than RAM (milliseconds vs nanoseconds).`}
          </div>
        </div>

        ${localHit > 0 || localRead > 0 ? `
        <div class="plain-info-item">
          <span class="item-icon">🔄</span>
          <div>
            <strong>3. Local Buffers (Stored Procedures &amp; Temp Tables): ${formatBlocksToBytes(localHit + localRead).formatted}</strong><br>
            PostgreSQL uses <strong>Local Buffers</strong> for session-private memory, temporary tables (<code>TEMP TABLE</code>), and PL/pgSQL loops inside stored functions.
            ${localHit > 10000 ? `Your plan touched <strong>${localHit.toLocaleString()} local blocks (~${formatBlocksToBytes(localHit).formatted})</strong>, which indicates that your stored procedure is heavily inserting, updating, or scanning temporary tables.` : 'Normal session-level memory activity.'}
          </div>
        </div>` : ''}

        ${totalDiskWrites > 0 || summary.hasDiskSpill ? `
        <div class="plain-info-item">
          <span class="item-icon">⚠️</span>
          <div>
            <strong style="color: #f43f5e;">4. Disk Spill Detected: ${diskWriteBytes.formatted} written to temporary files</strong><br>
            PostgreSQL ran out of allocated memory (<code>work_mem</code>) and had to dump sort buckets or hash tables to physical temporary files on disk. Spilling to disk drastically degrades query throughput. Increasing <code>work_mem</code> for this query or session can keep the operation in RAM.
          </div>
        </div>` : ''}
      </div>
    </div>

    <!-- Card 3: Cardinality & Cost Explained -->
    <div class="plain-card">
      <div class="plain-card-header">
        <div class="plain-card-title-group">
          <span class="plain-card-icon">🎯</span>
          <h3 class="plain-card-title">Planner Predictions vs Reality (Cardinality &amp; Cost)</h3>
        </div>
        <span class="plain-card-tag ${cardinalityMultiplier > 5.0 ? 'tag-rose' : 'tag-emerald'}">
          ${cardinalityMultiplier > 5.0 ? 'Estimate Drift Detected' : 'Good Estimates'}
        </span>
      </div>

      <div class="plain-info-list">
        <div class="plain-info-item">
          <span class="item-icon">📊</span>
          <div>
            <strong>Estimated Rows vs Actual Rows:</strong><br>
            The optimizer predicted <strong>${rowsEstimated != null ? rowsEstimated.toLocaleString() : 'N/A'} rows</strong>, but the database actually produced <strong>${rowsProduced != null ? rowsProduced.toLocaleString() : 'N/A'} rows</strong> (${cardinalityDiffText}).
            <br>
            <em>Why this matters:</em> The query planner relies on row estimates to choose join strategies. If it anticipates 1 row, it picks an index loop; if 100,000 rows show up, it will loop 100,000 times and crawl to a halt!
          </div>
        </div>

        <div class="plain-info-item">
          <span class="item-icon">🏷️</span>
          <div>
            <strong>Planner Cost (${summary.totalCost != null ? summary.totalCost.toLocaleString() : 'N/A'}):</strong><br>
            <strong>Cost is NOT time in milliseconds!</strong> It is an internal scoring unit used by PostgreSQL to compare different execution plans.
            A cost of <code>1.0</code> represents the estimated cost of reading one sequential 8 KB page from disk. Lower cost plans are preferred by the planner.
          </div>
        </div>
      </div>
    </div>

    <!-- Card 4: Plain English Glossary (What Every Info Means) -->
    <div class="plain-card">
      <div class="plain-card-header">
        <div class="plain-card-title-group">
          <span class="plain-card-icon">📖</span>
          <h3 class="plain-card-title">Quick Glossary: What Every Info Means</h3>
        </div>
        <span class="plain-card-tag tag-cyan">PostgreSQL Concepts</span>
      </div>

      <div class="glossary-grid">
        <div class="glossary-item">
          <div class="glossary-term">🟢 Shared Hit</div>
          <div class="glossary-def">Blocks read directly from shared memory (RAM). Ultra-fast (nanoseconds). Data was already in PostgreSQL's cache.</div>
        </div>

        <div class="glossary-item">
          <div class="glossary-term">🔴 Shared Read</div>
          <div class="glossary-def">Blocks read from storage (hard drive/SSD). Slower (milliseconds). Data was missing from RAM and had to be fetched.</div>
        </div>

        <div class="glossary-item">
          <div class="glossary-term">🔵 Local Hit / Read</div>
          <div class="glossary-def">Private memory used for temporary tables (<code>TEMP TABLE</code>) and stored procedure execution (PL/pgSQL).</div>
        </div>

        <div class="glossary-item">
          <div class="glossary-term">🟠 Dirtied Blocks</div>
          <div class="glossary-def">Blocks modified in RAM that have not yet been written to permanent storage disk by the checkpoint process.</div>
        </div>

        <div class="glossary-item">
          <div class="glossary-term">💾 Temp Written</div>
          <div class="glossary-def">Data spilled to disk because the sorting, hashing, or grouping operation exceeded the session's <code>work_mem</code> limit.</div>
        </div>

        <div class="glossary-item">
          <div class="glossary-term">🔁 Actual Loops</div>
          <div class="glossary-def">The number of times a plan node was executed. For example, in a Nested Loop join, the inner scan repeats once for every outer row.</div>
        </div>
      </div>
    </div>

    <!-- Card 5: Actionable Recommendations -->
    <div class="plain-card">
      <div class="plain-card-header">
        <div class="plain-card-title-group">
          <span class="plain-card-icon">🛠️</span>
          <h3 class="plain-card-title">Recommended Next Steps</h3>
        </div>
        <span class="plain-card-tag tag-emerald">Action Checklist</span>
      </div>

      <div class="checklist-container">
        ${findings.map((f, i) => `
          <div class="checklist-item ${f.severity === 'HIGH' ? 'urgent' : ''}">
            <span class="checklist-num">${i + 1}</span>
            <div>
              <strong>${escapeHtml(f.title)} (${f.severity})</strong><br>
              ${escapeHtml(f.explanation)}<br>
              <span style="color: var(--accent-cyan); font-size: 0.8rem;">👉 Action: ${escapeHtml(f.recommendation)}</span>
            </div>
          </div>
        `).join('')}

        ${findings.length === 0 ? `
          <div class="checklist-item">
            <span class="checklist-num">✓</span>
            <div>
              <strong>No critical bottlenecks detected by automated rules!</strong><br>
              The execution plan demonstrates good index usage and balanced resource consumption.
            </div>
          </div>
        ` : ''}
      </div>
    </div>
  `;

  container.innerHTML = html;
}

// ============================================================================
// Visual Charts Implementation
// ============================================================================

function resizeOrRedrawCharts() {
  if (bufferChartInstance) bufferChartInstance.resize();
  if (rowsChartInstance) rowsChartInstance.resize();
  if (timeChartInstance) timeChartInstance.resize();
}

function renderCharts(data) {
  const summary = data.summary || {};
  const root = data.planTree || {};

  // Buffer values in MB
  const sharedHitMB = ((summary.totalSharedHitBlocks || 0) * 8) / 1024;
  const sharedReadMB = ((summary.totalSharedReadBlocks || 0) * 8) / 1024;
  const localHitMB = ((summary.totalLocalHitBlocks || 0) * 8) / 1024;
  const localReadMB = ((summary.totalLocalReadBlocks || 0) * 8) / 1024;
  const tempSpillMB = (((summary.totalTempWrittenBlocks || 0) + (summary.totalLocalWrittenBlocks || 0)) * 8) / 1024;
  const totalDiskReadMB = sharedReadMB + localReadMB + (((summary.totalTempReadBlocks || 0) * 8) / 1024);

  // Rows
  const estRows = root.planRows != null ? Math.round(root.planRows) : 0;
  const actRows = summary.maxActualRows != null ? Math.round(summary.maxActualRows) : (root.actualRows != null ? Math.round(root.actualRows) : 0);

  // Time
  const execTime = summary.executionTimeMs != null ? summary.executionTimeMs : (root.actualTotalTime || 0);
  const planTime = summary.planningTimeMs != null ? summary.planningTimeMs : 0;

  const hasChartJs = typeof Chart !== 'undefined';

  renderBufferChart(sharedHitMB, localHitMB, totalDiskReadMB, tempSpillMB, hasChartJs, summary);
  renderRowsChart(estRows, actRows, hasChartJs);
  renderTimeChart(execTime, planTime, hasChartJs);
}

function renderBufferChart(sharedHitMB, localHitMB, totalDiskReadMB, tempSpillMB, hasChartJs, summary) {
  const canvas = document.getElementById('bufferChartCanvas');
  const fallback = document.getElementById('bufferChartFallback');
  if (!canvas) return;

  const totalMB = sharedHitMB + localHitMB + totalDiskReadMB + tempSpillMB;

  if (hasChartJs) {
    canvas.classList.remove('hidden');
    if (fallback) fallback.classList.add('hidden');

    if (bufferChartInstance) {
      bufferChartInstance.destroy();
      bufferChartInstance = null;
    }

    const labels = ['Shared RAM Hits', 'Local Session Hits', 'Physical Disk Reads', 'Disk Spills / Temp'];
    const dataValues = [
      parseFloat(sharedHitMB.toFixed(2)),
      parseFloat(localHitMB.toFixed(2)),
      parseFloat(totalDiskReadMB.toFixed(2)),
      parseFloat(tempSpillMB.toFixed(2))
    ];
    const colors = ['#10b981', '#00f2fe', '#f59e0b', '#f43f5e'];

    // If total is 0 (no buffer metrics in plan), show placeholder
    const isZero = totalMB === 0;

    bufferChartInstance = new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: isZero ? ['No Buffer Metrics in Plan'] : labels,
        datasets: [{
          data: isZero ? [1] : dataValues,
          backgroundColor: isZero ? ['rgba(255, 255, 255, 0.08)'] : colors,
          borderColor: '#131b26',
          borderWidth: 2,
          hoverOffset: 4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '62%',
        plugins: {
          legend: {
            position: 'right',
            labels: {
              color: '#94a3b8',
              font: { family: 'Outfit, sans-serif', size: 12 },
              padding: 14,
              boxWidth: 14
            }
          },
          tooltip: {
            callbacks: {
              label: function(context) {
                if (isZero) return 'Run with EXPLAIN (BUFFERS) to see cache distribution';
                const val = context.raw || 0;
                const pct = totalMB > 0 ? ((val / totalMB) * 100).toFixed(1) : 0;
                return ` ${context.label}: ${val.toLocaleString()} MB (${pct}%)`;
              }
            }
          }
        }
      }
    });
  } else {
    // Offline Fallback Meter
    canvas.classList.add('hidden');
    if (fallback) {
      fallback.classList.remove('hidden');
      const ramMB = sharedHitMB + localHitMB;
      const ramPct = totalMB > 0 ? ((ramMB / totalMB) * 100).toFixed(1) : 100;
      const diskPct = totalMB > 0 ? (((totalDiskReadMB + tempSpillMB) / totalMB) * 100).toFixed(1) : 0;

      fallback.innerHTML = `
        <div class="meter-row">
          <div class="meter-header">
            <span>RAM Cache Hits (Shared &amp; Local)</span>
            <span>${ramMB.toFixed(2)} MB (${ramPct}%)</span>
          </div>
          <div class="meter-track">
            <div class="meter-fill fill-ram" style="width: ${ramPct}%;"></div>
          </div>
        </div>

        <div class="meter-row">
          <div class="meter-header">
            <span>Disk I/O &amp; Spills</span>
            <span>${(totalDiskReadMB + tempSpillMB).toFixed(2)} MB (${diskPct}%)</span>
          </div>
          <div class="meter-track">
            <div class="meter-fill fill-disk" style="width: ${diskPct}%;"></div>
          </div>
        </div>

        <div class="chart-metric-callout">
          <span>Total Memory Processed: <strong>${totalMB.toFixed(2)} MB</strong></span>
          <span>Disk Latency Avoidance: <strong>${ramPct}% in RAM</strong></span>
        </div>
      `;
    }
  }
}

function renderRowsChart(estRows, actRows, hasChartJs) {
  const canvas = document.getElementById('rowsChartCanvas');
  const fallback = document.getElementById('rowsChartFallback');
  if (!canvas) return;

  if (hasChartJs) {
    canvas.classList.remove('hidden');
    if (fallback) fallback.classList.add('hidden');

    if (rowsChartInstance) {
      rowsChartInstance.destroy();
      rowsChartInstance = null;
    }

    rowsChartInstance = new Chart(canvas, {
      type: 'bar',
      data: {
        labels: ['Optimizer Estimate', 'Actual Produced'],
        datasets: [{
          label: 'Rows Count',
          data: [estRows, actRows],
          backgroundColor: [
            'rgba(148, 163, 184, 0.7)',
            'rgba(0, 242, 254, 0.85)'
          ],
          borderColor: [
            '#94a3b8',
            '#00f2fe'
          ],
          borderWidth: 1.5,
          borderRadius: 6
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: function(ctx) {
                return ` ${ctx.label}: ${ctx.raw.toLocaleString()} rows`;
              }
            }
          }
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { color: '#94a3b8', font: { family: 'Outfit, sans-serif', size: 12 } }
          },
          y: {
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: {
              color: '#94a3b8',
              callback: function(val) { return val.toLocaleString(); }
            }
          }
        }
      }
    });
  } else {
    // Offline Fallback Meter
    canvas.classList.add('hidden');
    if (fallback) {
      fallback.classList.remove('hidden');
      const maxRows = Math.max(estRows, actRows, 1);
      const estPct = ((estRows / maxRows) * 100).toFixed(1);
      const actPct = ((actRows / maxRows) * 100).toFixed(1);

      fallback.innerHTML = `
        <div class="meter-row">
          <div class="meter-header">
            <span>Planner Estimated Rows</span>
            <span>${estRows.toLocaleString()} rows</span>
          </div>
          <div class="meter-track">
            <div class="meter-fill fill-est" style="width: ${estPct}%;"></div>
          </div>
        </div>

        <div class="meter-row">
          <div class="meter-header">
            <span>Actual Produced Rows</span>
            <span>${actRows.toLocaleString()} rows</span>
          </div>
          <div class="meter-track">
            <div class="meter-fill fill-actual" style="width: ${actPct}%;"></div>
          </div>
        </div>

        <div class="chart-metric-callout">
          <span>Accuracy: <strong>${estRows > 0 ? (actRows / estRows).toFixed(1) + 'x ratio' : 'N/A'}</strong></span>
        </div>
      `;
    }
  }
}

function renderTimeChart(execTime, planTime, hasChartJs) {
  const canvas = document.getElementById('timeChartCanvas');
  const fallback = document.getElementById('timeChartFallback');
  if (!canvas) return;

  const totalTime = execTime + planTime;

  if (hasChartJs) {
    canvas.classList.remove('hidden');
    if (fallback) fallback.classList.add('hidden');

    if (timeChartInstance) {
      timeChartInstance.destroy();
      timeChartInstance = null;
    }

    timeChartInstance = new Chart(canvas, {
      type: 'bar',
      data: {
        labels: ['Execution Time', 'Planning Time'],
        datasets: [{
          label: 'Milliseconds',
          data: [parseFloat(execTime.toFixed(3)), parseFloat(planTime.toFixed(3))],
          backgroundColor: [
            'rgba(139, 92, 246, 0.85)',
            'rgba(245, 158, 11, 0.85)'
          ],
          borderColor: [
            '#8b5cf6',
            '#f59e0b'
          ],
          borderWidth: 1.5,
          borderRadius: 6
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: function(ctx) {
                const val = ctx.raw || 0;
                const pct = totalTime > 0 ? ((val / totalTime) * 100).toFixed(1) : 0;
                return ` ${ctx.label}: ${val} ms (${pct}%)`;
              }
            }
          }
        },
        scales: {
          x: {
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: {
              color: '#94a3b8',
              callback: function(val) { return val + ' ms'; }
            }
          },
          y: {
            grid: { display: false },
            ticks: { color: '#94a3b8', font: { family: 'Outfit, sans-serif', size: 12 } }
          }
        }
      }
    });
  } else {
    // Offline Fallback Meter
    canvas.classList.add('hidden');
    if (fallback) {
      fallback.classList.remove('hidden');
      const execPct = totalTime > 0 ? ((execTime / totalTime) * 100).toFixed(1) : 100;
      const planPct = totalTime > 0 ? ((planTime / totalTime) * 100).toFixed(1) : 0;

      fallback.innerHTML = `
        <div class="meter-row">
          <div class="meter-header">
            <span>Execution Time</span>
            <span>${execTime.toFixed(2)} ms (${execPct}%)</span>
          </div>
          <div class="meter-track">
            <div class="meter-fill fill-exec" style="width: ${execPct}%;"></div>
          </div>
        </div>

        <div class="meter-row">
          <div class="meter-header">
            <span>Planning Time</span>
            <span>${planTime.toFixed(3)} ms (${planPct}%)</span>
          </div>
          <div class="meter-track">
            <div class="meter-fill fill-plan" style="width: ${planPct}%;"></div>
          </div>
        </div>

        <div class="chart-metric-callout">
          <span>Total Query Latency: <strong>${totalTime.toFixed(2)} ms</strong></span>
        </div>
      `;
    }
  }
}

