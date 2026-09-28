// Mirrors the backend's DTO shapes (see backend/src/main/java/com/flowdesk/**/dto)
// closely enough for the UI's needs - not a full 1:1 transcription of every
// field the API returns, only the ones actually rendered or submitted here.

export type Role = "SUPER_ADMIN" | "ORG_ADMIN" | "TEAM_LEAD" | "AGENT" | "USER";

export type TicketStatus = "OPEN" | "IN_PROGRESS" | "WAITING" | "RESOLVED" | "CLOSED";

export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface UserSummary {
  id: number;
  organizationId: number | null;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  active: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface ProjectResponse {
  id: number;
  organizationId: number;
  name: string;
  description: string | null;
  status: "ACTIVE" | "ARCHIVED";
}

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

export interface CommentResponse {
  id: number;
  ticketId: number;
  authorId: number;
  authorName: string;
  body: string;
  createdAt: string;
  updatedAt: string;
}

export interface NotificationResponse {
  id: number;
  ticketId: number;
  type: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page, 0-indexed
  size: number;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}
