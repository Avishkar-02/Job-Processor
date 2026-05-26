/**
 * JobSubmitter.jsx
 *
 * Responsibility: let the user fire a POST /jobs request.
 *
 * UX notes:
 * - Button is disabled while a request is in-flight (prevents double submit).
 * - Rate limit errors (429) get a specific, helpful message.
 * - On success, calls onJobCreated(id) so App can add to trackedJobs.
 *
 * CONNECTION TO BACKEND:
 * The backend uses Redis to count POST /jobs requests per IP per minute.
 * Max 5 before a 429. We surface that here so the user understands why
 * clicking again won't help immediately.
 */

import { useState } from "react";
import { createJob } from "../api";

export default function JobSubmitter({ onJobCreated }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [lastCreated, setLastCreated] = useState(null);

  async function handleSubmit() {
    setLoading(true);
    setError(null);
    setLastCreated(null);

    try {
      const res = await createJob();
      // res.data = PostJobResponse { id, status, createdAt }
      const job = res.data;
      setLastCreated(job.id);
      onJobCreated(job.id); // bubble up to App so tracker picks it up
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="card submit-card">
      <h2 className="card-title">
        <span className="dot dot-green" />
        Submit Job
      </h2>

      <p className="card-desc">
        Creates a new background job. The backend saves it to MySQL, publishes
        the ID to Kafka, and a consumer thread picks it up for processing.
      </p>

      <button
        className={`btn-submit ${loading ? "loading" : ""}`}
        onClick={handleSubmit}
        disabled={loading}
      >
        {loading ? (
          <>
            <span className="spinner" /> Submitting…
          </>
        ) : (
          <>
            <span className="icon-plus">+</span> Create Job
          </>
        )}
      </button>

      {/* Success feedback */}
      {lastCreated && !error && (
        <div className="feedback feedback-success">
          ✓ Job <code>#{lastCreated}</code> created — tracking below ↓
        </div>
      )}

      {/* Error feedback: rate limit gets special treatment */}
      {error && (
        <div className="feedback feedback-error">
          <strong>Error:</strong> {error}
          {error.includes("Rate limit") && (
            <span className="rate-hint">
              {" "}
              Wait ~1 min or check your IP limit in Redis.
            </span>
          )}
        </div>
      )}

      {/* Rate limit explainer — always visible to set expectations */}
      <div className="rate-info">
        <span className="rate-badge">5 req / min</span>
        Rate-limited per IP via Redis sliding counter.
      </div>
    </div>
  );
}
