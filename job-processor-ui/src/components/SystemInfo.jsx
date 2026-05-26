/**
 * SystemInfo.jsx
 *
 * Static informational panel explaining what the system does.
 * No state, no API calls — pure presentational.
 *
 * Useful for demos and interviews: when someone opens the UI,
 * they immediately see the tech stack and data flow.
 */

export default function SystemInfo() {
  const layers = [
    {
      icon: "📨",
      label: "Kafka",
      desc: "Decouples job creation from execution. Producer publishes job ID; 3 parallel consumers pick it up.",
    },
    {
      icon: "⚡",
      label: "Redis (hot state)",
      desc: "RUNNING jobs cached as hash job:{id}. TTL 2 min. GET /jobs/{id} reads Redis first, MySQL on miss.",
    },
    {
      icon: "🗄",
      label: "MySQL (persistent)",
      desc: "Source of truth. Every status transition is written here. Redis is a performance layer on top.",
    },
    {
      icon: "🛡",
      label: "Rate Limiter",
      desc: "POST /jobs limited to 5/min per IP. Counter stored in Redis with 1-min expiry (sliding window).",
    },
    {
      icon: "🔒",
      label: "Idempotency",
      desc: "Consumer checks job.status == PENDING before processing. Duplicate Kafka deliveries are no-ops.",
    },
  ];

  return (
    <div className="card info-card">
      <h2 className="card-title">
        <span className="dot dot-blue" />
        How It Works
      </h2>

      <ul className="info-list">
        {layers.map((l) => (
          <li key={l.label} className="info-item">
            <span className="info-icon">{l.icon}</span>
            <div>
              <strong className="info-label">{l.label}</strong>
              <p className="info-desc">{l.desc}</p>
            </div>
          </li>
        ))}
      </ul>

      <div className="flow-diagram">
        <span className="flow-step">POST /jobs</span>
        <span className="flow-arrow">→</span>
        <span className="flow-step">MySQL save</span>
        <span className="flow-arrow">→</span>
        <span className="flow-step">Kafka publish</span>
        <span className="flow-arrow">→</span>
        <span className="flow-step">Consumer × 3</span>
        <span className="flow-arrow">→</span>
        <span className="flow-step">Redis + MySQL update</span>
      </div>
    </div>
  );
}
