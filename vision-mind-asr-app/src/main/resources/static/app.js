const state = {
  file: null,
  modalMode: null,
  hotwords: [],
  rules: [],
};

const $ = (id) => document.getElementById(id);

const renderJson = (value) => JSON.stringify(value, null, 2);

function setMeta(id, text, isError = false) {
  const el = $(id);
  el.textContent = text;
  el.style.color = isError ? "#a11c1c" : "";
}

async function parseJson(response) {
  const data = await response.json();
  if (!response.ok || data.code !== "0") {
    throw new Error(data.msg || `请求失败: ${response.status}`);
  }
  return data.data;
}

async function loadHealth() {
  try {
    const data = await parseJson(await fetch("./api/v1/asr/health"));
    setMeta("health-meta", `运行状态: ${data.ready ? "可用" : "未就绪"} | 模型: ${data.modelDir}`);
  } catch (error) {
    setMeta("health-meta", error.message, true);
  }
}

async function loadHotwords() {
  state.hotwords = (await parseJson(await fetch("./api/v1/asr/hotwords"))).baseTerms || [];
}

async function loadRules() {
  state.rules = (await parseJson(await fetch("./api/v1/asr/phrase-rules"))).rules || [];
}

function openModal(mode) {
  state.modalMode = mode;
  $("modal").classList.remove("hidden");
  $("modal").setAttribute("aria-hidden", "false");
  $("modal-status").textContent = "等待编辑";
  const body = $("modal-body");

  if (mode === "hotwords") {
    $("modal-title").textContent = "编辑热词";
    body.innerHTML = `
      <label class="field">
        <span>全局热词</span>
        <textarea id="modal-hotwords" class="config-textarea" placeholder="一行一个词">${state.hotwords.join("\n")}</textarea>
      </label>
    `;
    return;
  }

  $("modal-title").textContent = "编辑近音词规则";
  body.innerHTML = `
    <label class="field">
      <span>规则列表</span>
      <textarea id="modal-rules" class="config-textarea" placeholder="每行格式：误词1, 误词2 => 正确词">${state.rules
        .map((item) => `${(item.patterns || []).join(", ")} => ${item.replacement || ""}`)
        .join("\n")}</textarea>
    </label>
  `;
}

function closeModal() {
  $("modal").classList.add("hidden");
  $("modal").setAttribute("aria-hidden", "true");
}

async function saveModal() {
  try {
    if (state.modalMode === "hotwords") {
      const baseTerms = $("modal-hotwords").value
        .split(/\r?\n/)
        .map((item) => item.trim())
        .filter(Boolean);
      await parseJson(
        await fetch("./api/v1/asr/hotwords", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ baseTerms }),
        }),
      );
      await loadHotwords();
    } else {
      const lines = $("modal-rules").value
        .split(/\r?\n/)
        .map((item) => item.trim())
        .filter(Boolean);
      await parseJson(
        await fetch("./api/v1/asr/phrase-rules", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ lines }),
        }),
      );
      await loadRules();
    }
    $("modal-status").textContent = "保存成功";
    loadHealth();
  } catch (error) {
    $("modal-status").textContent = error.message;
  }
}

async function submitAsr() {
  if (!state.file) {
    setMeta("asr-meta", "请先选择音频文件", true);
    return;
  }

  const formData = new FormData();
  formData.append("file", state.file);
  formData.append("enablePunctuation", $("enable-punctuation").checked ? "true" : "false");

  setMeta("asr-meta", "识别中...");
  const startedAt = performance.now();
  try {
    const data = await parseJson(
      await fetch("./api/v1/asr/transcribe", {
        method: "POST",
        body: formData,
      }),
    );
    const elapsedMs = Math.round(performance.now() - startedAt);
    $("raw-text").value = data.rawText || "";
    $("phrase-text").value = data.textAfterPhrase || "";
    $("audio-info").textContent = renderJson(data.audioInfo || {});
    $("rule-info").textContent = renderJson(data.appliedRules || []);
    setMeta(
      "asr-meta",
      `识别完成，耗时 ${elapsedMs} ms，热词 ${((data.hotwords || []).length)} 个，标点恢复 ${data.punctuationEnabled ? "开" : "关"}`,
    );
  } catch (error) {
    setMeta("asr-meta", error.message, true);
  }
}

function bindEvents() {
  $("audio-file").addEventListener("change", (event) => {
    state.file = event.target.files?.[0] || null;
    $("file-name").textContent = state.file ? state.file.name : "未选择文件";
  });
  $("refresh-health").addEventListener("click", loadHealth);
  $("open-hotwords").addEventListener("click", async () => {
    await loadHotwords();
    openModal("hotwords");
  });
  $("open-rules").addEventListener("click", async () => {
    await loadRules();
    openModal("rules");
  });
  $("close-modal").addEventListener("click", closeModal);
  $("save-modal").addEventListener("click", saveModal);
  $("submit-asr").addEventListener("click", submitAsr);
  $("modal").addEventListener("click", (event) => {
    if (event.target.dataset.close === "true") {
      closeModal();
    }
  });
}

async function bootstrap() {
  bindEvents();
  await Promise.all([loadHealth(), loadHotwords(), loadRules()]);
}

bootstrap();
