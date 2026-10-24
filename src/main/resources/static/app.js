// Course Planner front end. All prerequisite checking happens on the server;
// this file only calls the REST API and renders what it returns.
"use strict";

const COMPLETED = "completed"; // column key for the Completed / Transfer column

const state = {
  plans: [],        // [{id, name, updatedAt}]
  plan: null,       // full PlanView from the API
  active: null,     // column key that "Add" targets: COMPLETED or a semester id
  selected: null,   // selected search result id
  selectedCard: null, // {column, id}
};

const $ = (id) => document.getElementById(id);
const enc = encodeURIComponent;

// ---- API ----------------------------------------------------------------

async function api(method, path, body) {
  const options = { method, headers: {} };
  if (body !== undefined) {
    options.headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(body);
  }
  const res = await fetch(path, options);
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error((data && (data.detail || data.title)) || res.statusText);
  return data;
}

const planPath = (suffix = "") => `/api/plans/${state.plan.id}${suffix}`;

/** Runs an API call that returns the updated plan, then re-renders. */
async function updatePlan(call) {
  try {
    state.plan = await call();
    renderBoard();
    refreshSearch();
  } catch (e) {
    toast(e.message);
  }
}

// ---- Small DOM helper ------------------------------------------------------

/** el("div", {class: "x", onclick: fn}, child, "text", ...) — text is never parsed as HTML. */
function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null || v === false) continue;
    if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
    else if (k === "class") node.className = v;
    else node.setAttribute(k, v === true ? "" : v);
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

let toastTimer;
function toast(message) {
  const t = $("toast");
  t.textContent = message;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.hidden = true), 4000);
}

// ---- Plans -----------------------------------------------------------------

async function loadPlans(selectId) {
  state.plans = await api("GET", "/api/plans");
  if (state.plans.length === 0) {
    const created = await api("POST", "/api/plans");
    state.plans = [created];
    selectId = created.id;
  }
  const remembered = Number(localStorage.getItem("planId"));
  const id = selectId
    ?? (state.plans.some((p) => p.id === remembered) ? remembered : state.plans[0].id);
  await openPlan(id);
}

async function openPlan(id) {
  state.plan = await api("GET", `/api/plans/${id}`);
  try { localStorage.setItem("planId", String(id)); } catch { /* storage unavailable */ }
  state.active = state.plan.semesters[0]?.id ?? COMPLETED;
  state.selectedCard = null;
  renderPlanSelect();
  renderBoard();
  refreshSearch();
}

function renderPlanSelect() {
  const select = $("plan-select");
  select.replaceChildren(...state.plans.map((p) =>
    el("option", { value: p.id, selected: p.id === state.plan.id }, p.name)));
}

$("plan-select").addEventListener("change", (e) => openPlan(Number(e.target.value)).catch((err) => toast(err.message)));

$("new-plan").addEventListener("click", async () => {
  const name = window.prompt("Name for the new plan:", "My plan");
  if (name === null) return;
  try {
    const created = await api("POST", "/api/plans", { name });
    await loadPlans(created.id);
  } catch (e) { toast(e.message); }
});

$("rename-plan").addEventListener("click", async () => {
  const name = window.prompt("Rename plan:", state.plan.name);
  if (name === null) return;
  try {
    state.plan = await api("PATCH", planPath(), { name });
    state.plans = await api("GET", "/api/plans");
    renderPlanSelect();
  } catch (e) { toast(e.message); }
});

$("delete-plan").addEventListener("click", async () => {
  if (!window.confirm(`Delete "${state.plan.name}"? This can't be undone.`)) return;
  try {
    await api("DELETE", planPath());
    localStorage.removeItem("planId");
    await loadPlans();
  } catch (e) { toast(e.message); }
});

// ---- Search ----------------------------------------------------------------

let searchTimer;
let searchSeq = 0;

function refreshSearch() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(runSearch, 120);
}

async function runSearch() {
  const seq = ++searchSeq;
  const q = $("search").value;
  const planParam = state.plan ? `&planId=${state.plan.id}` : "";
  try {
    const results = await api("GET", `/api/courses?q=${enc(q)}&limit=200${planParam}`);
    if (seq !== searchSeq) return; // a newer search already finished
    renderResults(results);
  } catch (e) { toast(e.message); }
}

const BADGE_LABEL = { READY: "Ready", CHECK: "Check", LOCKED: "Locked", IN_PLAN: "In plan" };

function renderResults(results) {
  $("result-count").textContent = results.length >= 200
    ? "Showing first 200 matches — keep typing to narrow"
    : `${results.length} match${results.length === 1 ? "" : "es"}`;
  $("results").replaceChildren(...results.map((c) => el("li", {
    class: c.id === state.selected ? "selected" : null,
    draggable: "true",
    title: c.hint,
    onclick: () => selectResult(c.id),
    ondblclick: () => addToActive(c.id),
    ondragstart: (e) => e.dataTransfer.setData("text/plain", c.id),
  },
    el("span", { class: "code" }, c.id),
    el("span", { class: "name" }, c.name),
    c.credits && el("span", { class: "credits" }, `${c.credits} cr`),
    c.availability && el("span", { class: `badge ${c.availability}` }, BADGE_LABEL[c.availability]),
  )));
}

function selectResult(id) {
  state.selected = id;
  for (const li of $("results").children) {
    li.classList.toggle("selected", li.querySelector(".code").textContent === id);
  }
  $("add-selected").disabled = false;
  showDetails(id);
}

$("search").addEventListener("input", refreshSearch);
$("search").addEventListener("keydown", (e) => {
  const first = $("results").firstElementChild;
  if (e.key === "Enter" && first) selectResult(first.querySelector(".code").textContent);
});
$("add-selected").addEventListener("click", () => state.selected && addToActive(state.selected));

// ---- Details ---------------------------------------------------------------

async function showDetails(id, card) {
  let c;
  try {
    c = await api("GET", `/api/courses/${enc(id)}`);
  } catch (e) { toast(e.message); return; }

  const p = c.prerequisites;
  const link = (courseId) => el("button", { class: "chip", onclick: () => showDetails(courseId) }, courseId);

  $("details").replaceChildren(
    el("h2", {}, `${c.id} — ${c.name}`),
    el("p", { class: "muted" },
      `${c.credits || "—"} credits · Offered: ${c.offered.length ? c.offered.join(", ") : "not listed"}`),
    card && card.status !== "DONE" && [
      el("h3", {}, "Status in this plan"),
      el("ul", {}, card.messages.map((m) => el("li", {}, m))),
    ],
    el("h3", {}, "Prerequisites"),
    el("p", {}, p.text || "None"),
    p.summary && el("p", { class: "muted" }, `Checked as: ${p.summary}`),
    p.notes.length > 0 && el("p", { class: "muted" }, `Not checked automatically: ${p.notes.join("; ")}`),
    p.concurrentOk && el("p", { class: "muted" }, "Some prerequisites may be taken in the same semester."),
    c.restrictions && [el("h3", {}, "Restrictions"), el("p", {}, c.restrictions)],
    c.description && [el("h3", {}, "Description"), el("p", {}, c.description)],
    c.prerequisiteFor.length > 0 && [
      el("h3", {}, "Prerequisite for"),
      el("div", { class: "chips" }, c.prerequisiteFor.map(link)),
    ],
  );
}

// ---- Board -----------------------------------------------------------------

function columnName(key) {
  if (key === COMPLETED) return "Completed / Transfer";
  return state.plan.semesters.find((s) => s.id === key)?.name ?? "";
}

function setActive(key) {
  state.active = key;
  for (const col of $("board").querySelectorAll(".column")) {
    col.classList.toggle("active", col.dataset.key === String(key));
  }
  $("add-selected").textContent = `Add selected course to → ${columnName(key)}`;
}

/** Put a course in a column. The server moves it if it's already elsewhere in the plan. */
function place(key, courseId) {
  setActive(key);
  const path = key === COMPLETED
    ? planPath(`/completed/${enc(courseId)}`)
    : planPath(`/semesters/${key}/courses/${enc(courseId)}`);
  return updatePlan(() => api("PUT", path));
}

function addToActive(courseId) {
  if (state.active === null) return toast("Add a semester first");
  place(state.active, courseId);
}

function removeCourse(key, courseId) {
  const path = key === COMPLETED
    ? planPath(`/completed/${enc(courseId)}`)
    : planPath(`/semesters/${key}/courses/${enc(courseId)}`);
  updatePlan(() => api("DELETE", path));
}

function renderBoard() {
  const { plan } = state;
  const columns = [
    renderColumn(COMPLETED, "Completed / Transfer", plan.completedCredits, plan.completed),
    ...plan.semesters.map((s) => renderColumn(s.id, s.name, s.credits, s.courses)),
    el("button", { class: "add-column", type: "button",
      onclick: () => updatePlan(() => api("POST", planPath("/semesters"))) }, "+ Add semester"),
  ];
  $("board").replaceChildren(...columns);
  const stillExists = state.active === COMPLETED || plan.semesters.some((s) => s.id === state.active);
  setActive(stillExists ? state.active : (plan.semesters[0]?.id ?? COMPLETED));
}

function renderColumn(key, name, credits, cards) {
  const isSemester = key !== COMPLETED;
  const title = el("div", { class: "title" },
    el("h2", { title: isSemester ? "Double-click to rename" : null,
      ondblclick: isSemester ? (e) => startRename(e.currentTarget, key, name) : null }, name),
    el("div", { class: "credits" }, `${credits} credits`));

  const column = el("div", {
    class: "column",
    "data-key": key,
    onclick: () => setActive(key),
    ondragover: (e) => { e.preventDefault(); column.classList.add("drop"); },
    ondragleave: (e) => { if (!column.contains(e.relatedTarget)) column.classList.remove("drop"); },
    ondrop: (e) => {
      e.preventDefault();
      column.classList.remove("drop");
      const courseId = e.dataTransfer.getData("text/plain");
      if (courseId) place(key, courseId);
    },
  },
    el("div", { class: "column-head" },
      title,
      isSemester && el("button", { class: "icon-btn", type: "button", title: "Delete semester",
        onclick: (e) => { e.stopPropagation(); deleteSemester(key, name, cards.length); } }, "×")),
    el("div", { class: "cards" },
      cards.length === 0 ? el("div", { class: "empty" }, "Drag courses here") : cards.map((c) => renderCard(key, c))),
  );
  return column;
}

function renderCard(key, card) {
  const status = card.status.toLowerCase();
  const extra = card.messages.length > 1 ? ` (+${card.messages.length - 1} more)` : "";
  const symbol = { ok: "✓", warn: "!", error: "✗" }[status];
  const isSelected = state.selectedCard?.column === key && state.selectedCard?.id === card.id;
  return el("div", {
    class: `card ${status}${isSelected ? " selected" : ""}`,
    draggable: "true",
    title: [`${card.id} ${card.name}`, ...card.messages.map((m) => `• ${m}`)].join("\n"),
    ondragstart: (e) => e.dataTransfer.setData("text/plain", card.id),
    onclick: (e) => {
      for (const c of $("board").querySelectorAll(".card.selected")) c.classList.remove("selected");
      e.currentTarget.classList.add("selected");
      state.selectedCard = { column: key, id: card.id };
      showDetails(card.id, card);
    },
  },
    el("div", { class: "stripe" }),
    el("div", { class: "body" },
      el("div", { class: "row" },
        el("span", { class: "code" }, card.id),
        card.credits && el("span", { class: "credits" }, `${card.credits} cr`),
        el("button", { class: "icon-btn", type: "button", title: "Remove",
          onclick: (e) => { e.stopPropagation(); removeCourse(key, card.id); } }, "×")),
      el("div", { class: "name" }, card.name),
      symbol && el("div", { class: "status" }, `${symbol} ${card.messages[0]}${extra}`)),
  );
}

function startRename(heading, semesterId, current) {
  const input = el("input", { value: current, maxlength: "80" });
  let done = false;
  const finish = (save) => {
    if (done) return;
    done = true;
    const name = input.value.trim();
    if (save && name && name !== current) {
      updatePlan(() => api("PATCH", planPath(`/semesters/${semesterId}`), { name }));
    } else {
      input.replaceWith(heading);
    }
  };
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") finish(true);
    if (e.key === "Escape") finish(false);
  });
  input.addEventListener("blur", () => finish(true));
  heading.replaceWith(input);
  input.focus();
  input.select();
}

function deleteSemester(semesterId, name, courseCount) {
  if (courseCount > 0 && !window.confirm(`Delete ${name} and its ${courseCount} course(s)?`)) return;
  updatePlan(() => api("DELETE", planPath(`/semesters/${semesterId}`)));
}

// ---- Start -------------------------------------------------------------------

loadPlans().catch((e) => toast(`Could not load plans: ${e.message}`));
