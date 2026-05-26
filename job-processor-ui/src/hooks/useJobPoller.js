/**
 * useJobPoller.js — custom React hook for polling a single job's status.
 *
 * WHY a custom hook?
 * Polling logic (interval, cleanup, back-off) doesn't belong in the component
 * that renders the job card. Separating it keeps the component focused on UI
 * and makes testing easier.
 *
 * POLLING DESIGN:
 * We poll every 3 s while the job is PENDING or RUNNING.
 * Once it reaches COMPLETED / FAILED / CANCELLED we stop — terminal states.
 *
 * This mirrors what the backend does:
 *   - RUNNING jobs live in Redis (hot path, fast reads).
 *   - Terminal-state jobs: Redis TTL expires and the next poll goes to MySQL.
 * So we naturally stop polling right when Redis would evict the key anyway.
 *
 * INTERVAL CHOICE:
 * The simulated job takes ~25 s (5 iterations × 5 s sleep).
 * 3 s interval gives smooth progress updates without hammering the server.
 * The rate limiter is on POST (create), not GET, so polling is fine.
 */

import { useState, useEffect, useRef } from "react";
import { getJob } from "../api";

const POLL_INTERVAL_MS = 3000;

/**
 * Terminal statuses — stop polling when we reach any of these.
 * Matches JobStatus enum on the backend exactly.
 */
const TERMINAL = new Set(["COMPLETED", "FAILED", "CANCELLED"]);

export function useJobPoller(jobId) {
  const [jobData, setJobData] = useState(null);  // raw data from backend
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  /**
   * intervalRef lets us clear the interval from inside the fetch callback
   * (when we detect a terminal state) without stale closure issues.
   * useRef is the right tool because changing it doesn't trigger a re-render.
   */
  const intervalRef = useRef(null);

  useEffect(() => {
    if (!jobId) return;

    let cancelled = false; // guard against setting state after unmount

    async function poll() {
      try {
        const res = await getJob(jobId);
        if (cancelled) return;

        const data = res.data;
        setJobData(data);
        setLoading(false);

        // Stop polling once the job reaches a terminal state.
        // This is the key connection to the backend: Redis deletes the key
        // at job end (deleteJobStore), so further polls would hit MySQL — still
        // correct, but we save the round-trip by just stopping.
        if (TERMINAL.has(data?.status)) {
          clearInterval(intervalRef.current);
        }
      } catch (err) {
        if (cancelled) return;
        setError(err.message);
        setLoading(false);
        clearInterval(intervalRef.current); // stop on error too
      }
    }

    // Kick off immediately, then every POLL_INTERVAL_MS
    poll();
    intervalRef.current = setInterval(poll, POLL_INTERVAL_MS);

    // Cleanup: stop polling when the component unmounts or jobId changes
    return () => {
      cancelled = true;
      clearInterval(intervalRef.current);
    };
  }, [jobId]);

  return { jobData, error, loading };
}
