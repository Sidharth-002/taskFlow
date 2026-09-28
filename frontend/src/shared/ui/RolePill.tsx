import { ROLE_LABEL } from "../lib/permissions";
import type { Role } from "../types";

export function RolePill({ role }: { role: Role }) {
  return <span className={`pill role-pill role-${role.toLowerCase()}`}>{ROLE_LABEL[role]}</span>;
}
