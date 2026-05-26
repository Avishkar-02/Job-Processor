/**
 * JobTracker.jsx
 *
 * Renders one JobCard per tracked job ID.
 * Receives jobIds from App (the single source of truth for which jobs to watch).
 *
 * WHY keep jobIds in App (not here)?
 * JobSubmitter also needs to know about newly created jobs.
 * Lifting state to App keeps both components in sync without prop drilling pain.
 */

import JobCard from "./JobCard";

export default function JobTracker({ jobIds }) {
  if (jobIds.length === 0) {
    return (
      <div className="tracker-empty">
        <div className="empty-icon">⚙</div>
        <p>No jobs yet. Submit one to start tracking.</p>
        <p className="empty-hint">
          Jobs run asynchronously — Kafka fans them out to 3 parallel
          consumer threads. Progress updates every ~5 s via Redis polling.
        </p>
      </div>
    );
  }

  return (
    <div className="tracker-list">
      <h2 className="section-title">
        Active Jobs
        <span className="job-count">{jobIds.length}</span>
      </h2>
      {jobIds.map((id) => (
        /* key=id is fine — job IDs are unique auto-increment from MySQL */
        <JobCard key={id} jobId={id} />
      ))}
    </div>
  );
}
