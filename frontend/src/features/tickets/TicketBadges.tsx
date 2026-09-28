import { PRIORITY_META, STATUS_META, isOverdue } from "./workflow";
import type { TicketPriority, TicketStatus } from "./types";
import { daysUntil, shortDate } from "../../shared/lib/format";
import { Icon } from "../../shared/ui/Icon";

export function StatusPill({ status }: { status: TicketStatus }) {
  const meta = STATUS_META[status];
  return (
    <span className="pill status-pill" style={{ ["--c" as string]: meta.color }}>
      <span className="dot" />
      {meta.label}
    </span>
  );
}

/** Jira-style chevrons: more bars = more urgent. */
export function PriorityIcon({ priority, size = 16 }: { priority: TicketPriority; size?: number }) {
  const meta = PRIORITY_META[priority];
  const bars = 4 - meta.rank;
  return (
    <span className="prio-icon" style={{ color: meta.color }} title={`${meta.label} priority`}>
      <svg width={size} height={size} viewBox="0 0 16 16" aria-hidden="true">
        {[0, 1, 2, 3].map((i) => (
          <rect
            key={i}
            x={1 + i * 3.8}
            y={12 - i * 3}
            width={2.6}
            height={3 + i * 3}
            rx={1}
            fill="currentColor"
            opacity={i < bars ? 1 : 0.2}
          />
        ))}
      </svg>
    </span>
  );
}

export function PriorityPill({ priority }: { priority: TicketPriority }) {
  const meta = PRIORITY_META[priority];
  return (
    <span className="pill prio-pill" style={{ ["--c" as string]: meta.color }}>
      <PriorityIcon priority={priority} size={13} />
      {meta.label}
    </span>
  );
}

export function DueBadge({ dueDate, status }: { dueDate: string | null; status: TicketStatus }) {
  if (!dueDate) {
    return null;
  }
  const overdue = isOverdue({ dueDate, status });
  const days = daysUntil(dueDate);
  const soon = !overdue && days <= 2 && status !== "CLOSED" && status !== "RESOLVED";
  return (
    <span className={`due-badge ${overdue ? "overdue" : soon ? "soon" : ""}`} title={`Due ${shortDate(dueDate)}`}>
      <Icon name={overdue ? "flame" : "clock"} size={12} />
      {overdue ? `${Math.abs(days) || 0}d late` : shortDate(dueDate)}
    </span>
  );
}
