import type { Role } from "../types";

export const can = {
  editTicket: (role: Role) => role === "ORG_ADMIN" || role === "TEAM_LEAD" || role === "AGENT",
  assignTicket: (role: Role) => role === "ORG_ADMIN" || role === "TEAM_LEAD",
  deleteTicket: (role: Role) => role === "ORG_ADMIN",
  viewDashboardSummary: (role: Role) => role === "ORG_ADMIN" || role === "TEAM_LEAD",
  manageOrganization: (role: Role) => role === "ORG_ADMIN",
};

export const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: "Super admin",
  ORG_ADMIN: "Admin",
  TEAM_LEAD: "Team lead",
  AGENT: "Agent",
  USER: "Reporter",
};

export const ROLE_SCOPE: Record<Role, string> = {
  SUPER_ADMIN: "No organization tickets",
  ORG_ADMIN: "Every ticket in the organization",
  TEAM_LEAD: "Tickets for teams you lead",
  AGENT: "Tickets assigned to you",
  USER: "Tickets you reported",
};
