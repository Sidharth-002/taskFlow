import type { TicketListItem, TicketPriority, TicketStatus } from "./types";

export const STATUSES: TicketStatus[] = ["OPEN", "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED"];

export const PRIORITIES: TicketPriority[] = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];

export const TRANSITIONS: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ["IN_PROGRESS"],
  IN_PROGRESS: ["WAITING", "RESOLVED"],
  WAITING: ["IN_PROGRESS"],
  RESOLVED: ["CLOSED"],
  CLOSED: [],
};

export function canTransition(from: TicketStatus, to: TicketStatus): boolean {
  return from !== to && TRANSITIONS[from].includes(to);
}

export const STATUS_META: Record<TicketStatus, { label: string; verb: string; color: string }> = {
  OPEN: { label: "To do", verb: "Reopen", color: "var(--status-open)" },
  IN_PROGRESS: { label: "In progress", verb: "Start work", color: "var(--status-progress)" },
  WAITING: { label: "Waiting", verb: "Put on hold", color: "var(--status-waiting)" },
  RESOLVED: { label: "Resolved", verb: "Resolve", color: "var(--status-resolved)" },
  CLOSED: { label: "Closed", verb: "Close", color: "var(--status-closed)" },
};

export const PRIORITY_META: Record<TicketPriority, { label: string; color: string; rank: number }> = {
  CRITICAL: { label: "Critical", color: "var(--prio-critical)", rank: 0 },
  HIGH: { label: "High", color: "var(--prio-high)", rank: 1 },
  MEDIUM: { label: "Medium", color: "var(--prio-medium)", rank: 2 },
  LOW: { label: "Low", color: "var(--prio-low)", rank: 3 },
};

const DONE: TicketStatus[] = ["RESOLVED", "CLOSED"];

export function isDone(status: TicketStatus): boolean {
  return DONE.includes(status);
}

export function isOverdue(ticket: { dueDate: string | null; status: TicketStatus }): boolean {
  return !!ticket.dueDate && !isDone(ticket.status) && new Date(ticket.dueDate).getTime() < Date.now();
}

export function byPriorityThenAge(a: TicketListItem, b: TicketListItem): number {
  return PRIORITY_META[a.priority].rank - PRIORITY_META[b.priority].rank || b.createdAt.localeCompare(a.createdAt);
}
