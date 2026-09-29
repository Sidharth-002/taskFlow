// Cross-feature shapes mirroring the backend's DTOs. Feature-specific
// shapes live next to their feature (features/*/types.ts).

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

/** Large enough to fetch a whole organization's directory in one request (Spring's max page size is 2000). */
export const ALL = 500;
