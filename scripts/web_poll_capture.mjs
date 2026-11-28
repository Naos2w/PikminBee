// Test-only instrumentation for the isolated Web preview. Never retain payloads or headers.
export function installPollCapture(target = globalThis) {
  const originalFetch = target.fetch;
  const originalJson = target.Response.prototype.json;
  const now = () => target.performance.now();
  const start = now();
  const attempts = [];
  const responses = new WeakMap();
  target.fetch = async function(input, ...args) {
    // The packaged controller calls fetch with the literal relative path.
    if (input !== "/api/state") return originalFetch.call(this, input, ...args);
    const record = {startMs: now() - start, status: null, fetchEndMs: null,
      fetchOutcome: "pending", jsonStartMs: null, jsonEndMs: null, jsonOutcome: "not_started"};
    attempts.push(record);
    try {
      const response = await originalFetch.call(this, input, ...args);
      record.status = response.status;
      record.fetchOutcome = "http";
      responses.set(response, record);
      return response;
    } catch (error) {
      record.fetchOutcome = error?.name === "AbortError" ? "cancelled" : "transport_error";
      throw error;
    } finally {
      record.fetchEndMs = now() - start;
    }
  };
  target.Response.prototype.json = async function(...args) {
    const record = responses.get(this);
    if (!record || record.jsonStartMs !== null) return originalJson.apply(this, args);
    record.jsonStartMs = now() - start;
    record.jsonOutcome = "pending";
    try {
      const value = await originalJson.apply(this, args);
      record.jsonOutcome = "ok";
      return value;
    } catch (error) {
      record.jsonOutcome = "error";
      throw error;
    } finally {
      record.jsonEndMs = now() - start;
    }
  };
  return {
    start,
    snapshot() {
      const durationMs = now() - start;
      return {durationMs, attempts: attempts.map(record => ({...record}))};
    },
    restore() {
      target.fetch = originalFetch;
      target.Response.prototype.json = originalJson;
    }
  };
}
