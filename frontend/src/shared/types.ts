export type Role = "SUPER_ADMIN" | "ORG_ADMIN" | "TEAM_LEAD" | "AGENT" | "USER";

export interface UserSummary {
  id: number;
  organizationId: number | null;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  active: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
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

export const ALL = 500;
