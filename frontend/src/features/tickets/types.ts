export type TicketStatus = "OPEN" | "IN_PROGRESS" | "WAITING" | "RESOLVED" | "CLOSED";

export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface TicketListItem {
  id: number;
  title: string;
  status: TicketStatus;
  priority: TicketPriority;
  dueDate: string | null;
  projectId: number;
  projectName: string;
  teamId: number | null;
  teamName: string | null;
  assignedToId: number | null;
  assignedToName: string | null;
  createdById: number;
  createdByName: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface TicketDetail {
  id: number;
  organizationId: number;
  projectId: number;
  teamId: number | null;
  createdById: number;
  assignedToId: number | null;
  title: string;
  description: string | null;
  status: TicketStatus;
  priority: TicketPriority;
  dueDate: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface TicketFilters {
  status?: TicketStatus;
  priority?: TicketPriority;
  projectId?: number;
  teamId?: number;
  assignedToId?: number;
  search?: string;
}

export interface CommentResponse {
  id: number;
  ticketId: number;
  authorId: number;
  authorName: string;
  body: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuditLogResponse {
  id: number;
  ticketId: number;
  eventType: string;
  actorUserId: number | null;
  summary: string;
  occurredAt: string;
}
