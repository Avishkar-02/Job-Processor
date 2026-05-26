/**
 * JobCard.jsx
 *
 * Displays a single job's live status, progress bar, and cancel button.
 * Uses useJobPoller (custom hook) for all the fetching/interval logic.
 *
 * STATUS → VISUAL MAPPING:
 *   PENDING   → pulsing grey badge, 0% bar
 *   RUNNING   → animated teal badge, live progress bar (from Redis)
 *   COMPLETED → green badge, 100% bar, result text
 *   FAILED    → red badge, error message
 *   CANCELLED → amber badge, flat bar
 *
 * CANCEL BUTTON:
 * Calls DELETE /jobs/:id. The backend sets cancelFlag in ConcurrentHashMap
 * so the consumer's next 5-s sleep cycle sees isCanceled()==true and breaks.
 * We optimistically hide the button on cancel (the polling will confirm the
 * status change within one poll cycle, ~3 s).
 *
 * REDIS vs DB indicator:
 * RedisJobResponse has { id, status, progress } with NO result/errorMessage.
 * GetJobResponse (MySQL fallback) includes those extra fields.
 * We detect Redis hits by checking for absence of `createdAt` field
 * (not included in RedisJobResponse). This shows the user which path was used.
 */

import { useState } from "react";
import { cancelJob } from "../api";
import { useJobPoller } from "../hooks/useJobPoller";

const STATUS_CONFIG = {
  PENDING: {
    label: "PENDING",
    cls: "badge-pending",
    icon: "⏳",
    animate: true,
  },
  RUNNING: {
    label: "RUNNING",
    cls: "badge-running",
    icon: "⚙",
    animate: true,
  },
  COMPLETED: {
    label: "COMPLETED",
    cls: "badge-completed",
    icon: "✓",
    animate: false,
  },
  FAILED: {
    label: "FAILED",
    cls: "badge-failed",
    icon: "✗",
    animate: false,
  },
  CANCELLED: {
    label: "CANCELLED",
    cls: "badge-cancelled",
    icon: "◼",
    animate: false,
  },
};

const TERMINAL = new Set(["COMPLETED", "FAILED", "CANCELLED"]);

export default function JobCard({ jobId }) {
  const { jobData, error, loading } = useJobPoller(jobId);
  const [cancelState, setCancelState] = useState("idle"); // idle | cancelling | done | error
  const [cancelError, setCancelError] = useState(null);

  async function handleCancel() {
    setCancelState("cancelling");
    setCancelError(null);
    try {
      await cancelJob(jobId);
      setCancelState("done");
      // Poller will pick up CANCELLED status in next poll cycle
    } catch (err) {
      setCancelState("error");
      setCancelError(err.message);
    }
  }

  // Determine if this response came from Redis (hot path) or MySQL (fallback).
  // Redis responses: { id, status, progress }  — no createdAt, result, errorMessage
  // MySQL responses: full GetJobResponse with createdAt present
  const isRedisHit = jobData && !jobData.createdAt;

  const cfg = STATUS_CONFIG[jobData?.status] || STATUS_CONFIG.PENDING;
  const progress = jobData?.progress ?? 0;
  const status = jobData?.status ?? "PENDING";
  const isTerminal = TERMINAL.has(status);
  const canCancel =
    !isTerminal && cancelState !== "done" && cancelState !== "cancelling";

  return (
    <div className={`job-card ${cfg.cls.replace("badge-", "card-")}`}>
      {/* Header row */}
      <div className="job-card-header">
        <div className="job-id-group">
          <span className="job-id-label">Job</span>
          <code className="job-id">#{jobId}</code>
        </div>

        <div className="job-badge-group">
          {/* Redis/DB indicator — shows users the caching layer is working */}
          {jobData && (
            <span
              className={`cache-badge ${isRedisHit ? "cache-redis" : "cache-db"}`}
              title={
                isRedisHit
                  ? "Status served from Redis (hot cache)"
                  : "Status served from MySQL (cache miss or terminal state)"
              }
            >
              {isRedisHit ? "Redis ⚡" : "MySQL 💾"}
            </span>
          )}

          <span className={`status-badge ${cfg.cls} ${cfg.animate ? "badge-pulse" : ""}`}>
            <span className="badge-icon">{cfg.icon}</span>
            {cfg.label}
          </span>
        </div>
      </div>

      {/* Progress bar — shown for PENDING and RUNNING */}
      {!isTerminal && (
        <div className="progress-track" aria-label={`Progress: ${progress}%`}>
          <div
            className={`progress-fill ${status === "RUNNING" ? "fill-animated" : ""}`}
            style={{ width: `${progress}%` }}
          />
          <span className="progress-label">{progress}%</span>
        </div>
      )}

      {/* Terminal state details */}
      {status === "COMPLETED" && (
        <div className="job-detail detail-success">
          {jobData?.result || "Job completed successfully"}
        </div>
      )}
      {status === "FAILED" && (
        <div className="job-detail detail-error">
          {jobData?.errorMessage || "Job failed — check backend logs"}
        </div>
      )}
      {status === "CANCELLED" && (
        <div className="job-detail detail-warn">
          Job was cancelled. Consumer stops at the next 5-s checkpoint.
        </div>
      )}

      {/* Loading state (first poll not yet returned) */}
      {loading && (
        <div className="job-loading">
          <span className="spinner" /> Fetching status…
        </div>
      )}

      {/* Poll error */}
      {error && (
        <div className="job-detail detail-error">Poll error: {error}</div>
      )}

      {/* Footer row: cancel button + timestamps */}
      <div className="job-card-footer">
        {canCancel && (
          <button className="btn-cancel" onClick={handleCancel}>
            Cancel
          </button>
        )}
        {cancelState === "cancelling" && (
          <span className="cancel-feedback">
            <span className="spinner" /> Cancelling…
          </span>
        )}
        {cancelState === "done" && (
          <span className="cancel-feedback cancel-ok">Cancel sent ✓</span>
        )}
        {cancelState === "error" && (
          <span className="cancel-feedback cancel-err">
            Cancel failed: {cancelError}
          </span>
        )}

        {/* Show created/updated timestamps from MySQL responses */}
        {jobData?.createdAt && (
          <span className="timestamp">
            Created {new Date(jobData.createdAt).toLocaleTimeString()}
          </span>
        )}
      </div>
    </div>
  );
}
