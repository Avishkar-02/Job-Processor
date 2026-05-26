/**
 * App.jsx — Root component
 *
 * Why a single-page app?
 * The backend exposes three endpoints: POST /jobs, GET /jobs/{id}, DELETE /jobs/{id}.
 * All three are covered in one dashboard view, so we don't need routing.
 * State flows DOWN via props; actions flow UP via callbacks.
 */

import { useState } from "react";
import JobSubmitter from "./components/JobSubmitter";
import JobTracker from "./components/JobTracker";
import SystemInfo from "./components/SystemInfo";

export default function App() {
  /**
   * trackedJobs: array of job IDs the user submitted in this session.
   * We keep them here (root) so JobSubmitter can add IDs
   * and JobTracker can poll them — both need the same list.
   */
  const [trackedJobs, setTrackedJobs] = useState([]);

  /**
   * Called by JobSubmitter after a successful POST /jobs response.
   * Prepends so newest job appears at top of the list.
   */
  const handleJobCreated = (jobId) => {
    setTrackedJobs((prev) => [jobId, ...prev]);
  };

  return (
    <div className="app-shell">
      <header className="app-header">
        {/* Terminal-style blinking cursor in the title adds personality
            and hints at the async/processing nature of the system */}
        <h1 className="app-title">
          Job<span className="accent">_</span>Processor
          <span className="cursor">▮</span>
        </h1>
        <p className="app-subtitle">
          Async · Kafka · Redis · Spring Boot
        </p>
      </header>

      <main className="app-main">
        {/* Left column: submit + system info */}
        <aside className="sidebar">
          <JobSubmitter onJobCreated={handleJobCreated} />
          <SystemInfo />
        </aside>

        {/* Right column: live job tracker */}
        <section className="tracker-panel">
          <JobTracker jobIds={trackedJobs} />
        </section>
      </main>
    </div>
  );
}
