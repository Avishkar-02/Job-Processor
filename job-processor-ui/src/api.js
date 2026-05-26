/**
 * api.js — centralised HTTP client for the Job Processor backend.
 *
 * WHY this file exists:
 * Keeping fetch() calls scattered across components makes it painful
 * to change the base URL (e.g. when switching from localhost to Docker
 * service name). One place to change → everything updates.
 *
 * The backend CORS config must allow http://localhost:5173 (Vite dev)
 * and http://localhost (Docker nginx) for these calls to succeed.
 * In Docker we proxy /api through nginx to avoid CORS entirely —
 * see nginx.conf.
 */

/**
 * Base URL:
 * - In dev (Vite): Vite's proxy in vite.config.js rewrites /api → localhost:8080
 *   so we never hit CORS because the browser sees same origin.
 * - In Docker: nginx proxies /api → backend:8080, same trick, no CORS.
 * We never hardcode localhost:8080 — the proxy handles it.
 */
const BASE = "/api/jobs";

/**
 * Generic fetch wrapper.
 * Throws a descriptive Error on non-2xx so callers can catch and show UI.
 */
async function request(url, options = {}) {
  const res = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });

  // 429 from rate limiter: surface a friendly message
  if (res.status === 429) {
    throw new Error("Rate limit hit — max 5 jobs per minute per IP.");
  }

  // Parse JSON for all responses (success and error bodies share ApiResponse shape)
  const body = await res.json().catch(() => null);

  if (!res.ok) {
    // Backend wraps errors in ApiResponse<ErrorResponse>
    const msg =
      body?.data?.message || body?.message || `HTTP ${res.status}`;
    throw new Error(msg);
  }

  return body; // { success: true, data: ... }
}

/**
 * POST /jobs
 * Returns: ApiResponse<PostJobResponse> → { id, status, createdAt }
 */
export async function createJob() {
  return request(BASE, { method: "POST" });
}

/**
 * GET /jobs/:id
 * Returns: ApiResponse<GetJobResponse | RedisJobResponse>
 *   - If Redis HIT: { id, status, progress }         (RedisJobResponse)
 *   - If Redis MISS: { id, status, progress, result, ... } (GetJobResponse)
 * The shape is compatible enough that we read .status and .progress for both.
 */
export async function getJob(id) {
  return request(`${BASE}/${id}`);
}

/**
 * DELETE /jobs/:id
 * Returns: ApiResponse<CancelJobResponse> → { id, status }
 */
export async function cancelJob(id) {
  return request(`${BASE}/${id}`, { method: "DELETE" });
}
