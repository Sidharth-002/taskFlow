import type { ReactNode } from "react";
import { Logo } from "../../shared/ui/Feedback";
import { Icon, type IconName } from "../../shared/ui/Icon";

const FEATURES: { icon: IconName; title: string; text: string }[] = [
  { icon: "board", title: "Kanban that enforces your workflow", text: "Drag issues across the board - illegal moves are blocked before they happen." },
  { icon: "team", title: "Teams with real leads", text: "Route work to a team and its lead sees exactly what's theirs." },
  { icon: "zap", title: "Live everything", text: "Kafka-driven notifications and a full audit trail on every issue." },
];

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth-page">
      <div className="aurora" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <section className="auth-hero">
        <div className="auth-brand">
          <Logo size={44} />
          <span>FlowDesk</span>
        </div>
        <h1>
          Ship work at <span className="gradient-text">warp speed.</span>
        </h1>
        <p className="muted">Issue tracking for teams who'd rather be building.</p>
        <ul className="auth-features">
          {FEATURES.map((f) => (
            <li key={f.title}>
              <span className="auth-feature-icon">
                <Icon name={f.icon} />
              </span>
              <div>
                <strong>{f.title}</strong>
                <p className="muted">{f.text}</p>
              </div>
            </li>
          ))}
        </ul>
        <div className="floating-cards" aria-hidden="true">
          <div className="float-card c1">
            <span className="dot" style={{ background: "var(--status-progress)" }} /> FD-128 · Payment retries
          </div>
          <div className="float-card c2">
            <span className="dot" style={{ background: "var(--status-resolved)" }} /> FD-131 · Dark mode ✨
          </div>
          <div className="float-card c3">
            <span className="dot" style={{ background: "var(--prio-critical)" }} /> FD-140 · Prod is on fire 🔥
          </div>
        </div>
      </section>
      <section className="auth-panel">{children}</section>
    </div>
  );
}
