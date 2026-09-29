import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { listUsers } from "../features/people/api";
import { listTeams, type TeamResponse } from "../features/teams/api";
import { listProjects, type ProjectResponse } from "../features/projects/api";
import type { UserSummary } from "../shared/types";
import { fullName } from "../shared/lib/format";

// The organization's users, teams and projects, loaded once per session and
// shared by every page that needs a picker or has to resolve an id to a name
// (TicketDetail only carries ids). Pages that change one of these call the
// matching reload so every other page sees the change.

interface Directory {
  users: UserSummary[];
  teams: TeamResponse[];
  projects: ProjectResponse[];
  ready: boolean;
  userName: (id: number | null | undefined) => string | null;
  reloadUsers: () => Promise<void>;
  reloadTeams: () => Promise<void>;
  reloadProjects: () => Promise<void>;
}

const DirectoryContext = createContext<Directory | undefined>(undefined);

export function DirectoryProvider({ children }: { children: ReactNode }) {
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [teams, setTeams] = useState<TeamResponse[]>([]);
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [ready, setReady] = useState(false);

  const reloadUsers = useCallback(() => listUsers().then(setUsers), []);
  const reloadTeams = useCallback(() => listTeams().then(setTeams), []);
  const reloadProjects = useCallback(() => listProjects().then(setProjects), []);

  useEffect(() => {
    // One failing list (e.g. a transient error) shouldn't blank the others.
    Promise.allSettled([reloadUsers(), reloadTeams(), reloadProjects()]).finally(() => setReady(true));
  }, [reloadUsers, reloadTeams, reloadProjects]);

  const value = useMemo<Directory>(() => {
    const byId = new Map(users.map((u) => [u.id, u]));
    return {
      users,
      teams,
      projects,
      ready,
      userName: (id) => {
        const user = id == null ? undefined : byId.get(id);
        return user ? fullName(user) : null;
      },
      reloadUsers,
      reloadTeams,
      reloadProjects,
    };
  }, [users, teams, projects, ready, reloadUsers, reloadTeams, reloadProjects]);

  return <DirectoryContext.Provider value={value}>{children}</DirectoryContext.Provider>;
}

export function useDirectory(): Directory {
  const context = useContext(DirectoryContext);
  if (!context) {
    throw new Error("useDirectory must be used within a DirectoryProvider");
  }
  return context;
}
