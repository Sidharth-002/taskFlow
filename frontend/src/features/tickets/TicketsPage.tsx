import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { listTickets } from "./api";
import type { TicketFilters, TicketPriority, TicketStatus } from "./types";
import { PRIORITIES, PRIORITY_META, STATUSES, STATUS_META, isOverdue } from "./workflow";
import { DueBadge, PriorityPill, StatusPill } from "./TicketBadges";
import { useDirectory } from "../../app/DirectoryContext";
import { useShell } from "../../app/ShellContext";
import { useAsync, useDebounced, useOnTicketsChanged } from "../../shared/lib/hooks";
import { fullName, ticketKey, timeAgo } from "../../shared/lib/format";
import { Avatar } from "../../shared/ui/Avatar";
import { Icon } from "../../shared/ui/Icon";
import { EmptyState, ErrorBanner, Spinner } from "../../shared/ui/Feedback";

const PAGE_SIZE = 25;

type SortKey = "createdAt" | "updatedAt" | "dueDate" | "title";

export function TicketsPage() {
  const navigate = useNavigate();
  const { openCreateTicket } = useShell();
  const { projects, teams, users } = useDirectory();

  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<TicketStatus | "">("");
  const [priority, setPriority] = useState<TicketPriority | "">("");
  const [projectId, setProjectId] = useState<number | "">("");
  const [teamId, setTeamId] = useState<number | "">("");
  const [assignedToId, setAssignedToId] = useState<number | "">("");
  const [sort, setSort] = useState<{ key: SortKey; dir: "asc" | "desc" }>({ key: "createdAt", dir: "desc" });
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebounced(search.trim(), 300);

  const filters: TicketFilters = {
    search: debouncedSearch || undefined,
    status: status || undefined,
    priority: priority || undefined,
    projectId: projectId === "" ? undefined : projectId,
    teamId: teamId === "" ? undefined : teamId,
    assignedToId: assignedToId === "" ? undefined : assignedToId,
  };

  const { data, error, loading, reload } = useAsync(
    () => listTickets({ ...filters, page, size: PAGE_SIZE, sort: `${sort.key},${sort.dir}` }),
    [debouncedSearch, status, priority, projectId, teamId, assignedToId, sort.key, sort.dir, page],
  );
  useOnTicketsChanged(reload);

  // Any filter change goes back to the first page.
  function update<T>(setter: (v: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  function toggleSort(key: SortKey) {
    setSort((s) => ({ key, dir: s.key === key && s.dir === "desc" ? "asc" : "desc" }));
    setPage(0);
  }

  function SortHeader({ k, children }: { k: SortKey; children: string }) {
    const active = sort.key === k;
    return (
      <button className={`th-sort ${active ? "active" : ""}`} onClick={() => toggleSort(k)}>
        {children}
        {active && <span>{sort.dir === "asc" ? "↑" : "↓"}</span>}
      </button>
    );
  }

  const rows = data?.content ?? [];

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">
            <Icon name="list" size={14} /> Every issue you can see
          </p>
          <h1 className="gradient-text">Issues</h1>
          <p className="muted">{data ? `${data.totalElements} issues match` : "Loading…"}</p>
        </div>
        <button className="btn btn-primary" onClick={() => openCreateTicket()}>
          <Icon name="plus" /> Create issue
        </button>
      </header>

      <div className="filter-bar">
        <label className="search-field">
          <Icon name="search" size={16} />
          <input value={search} onChange={(e) => update(setSearch)(e.target.value)} placeholder="Search titles…" />
        </label>
        <select value={status} onChange={(e) => update(setStatus)(e.target.value as TicketStatus | "")}>
          <option value="">Any status</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {STATUS_META[s].label}
            </option>
          ))}
        </select>
        <select value={priority} onChange={(e) => update(setPriority)(e.target.value as TicketPriority | "")}>
          <option value="">Any priority</option>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {PRIORITY_META[p].label}
            </option>
          ))}
        </select>
        <select
          value={projectId}
          onChange={(e) => update(setProjectId)(e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">All projects</option>
          {projects.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <select value={teamId} onChange={(e) => update(setTeamId)(e.target.value ? Number(e.target.value) : "")}>
          <option value="">All teams</option>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        <select
          value={assignedToId}
          onChange={(e) => update(setAssignedToId)(e.target.value ? Number(e.target.value) : "")}
        >
          <option value="">Any assignee</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {fullName(u)}
            </option>
          ))}
        </select>
      </div>

      {error && <ErrorBanner message={error} onRetry={reload} />}

      <div className="panel table-panel">
        {loading && !data ? (
          <Spinner />
        ) : rows.length === 0 ? (
          <EmptyState
            icon="inbox"
            title="No issues here"
            action={
              <button className="btn btn-primary" onClick={() => openCreateTicket()}>
                <Icon name="plus" /> Create the first one
              </button>
            }
          >
            Try loosening the filters, or create a new issue.
          </EmptyState>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Key</th>
                <th>
                  <SortHeader k="title">Summary</SortHeader>
                </th>
                <th>Status</th>
                <th>Priority</th>
                <th>Assignee</th>
                <th>
                  <SortHeader k="dueDate">Due</SortHeader>
                </th>
                <th>
                  <SortHeader k="updatedAt">Updated</SortHeader>
                </th>
                <th>
                  <SortHeader k="createdAt">Created</SortHeader>
                </th>
              </tr>
            </thead>
            <tbody className={loading ? "is-loading" : ""}>
              {rows.map((t) => (
                <tr
                  key={t.id}
                  className={isOverdue(t) ? "row-overdue" : ""}
                  onClick={() => navigate(`/tickets/${t.id}`)}
                >
                  <td className="mono">{ticketKey(t.id)}</td>
                  <td className="cell-title">
                    <Link to={`/tickets/${t.id}`} onClick={(e) => e.stopPropagation()}>
                      {t.title}
                    </Link>
                    <small className="muted">
                      {t.projectName}
                      {t.teamName ? ` · ${t.teamName}` : ""}
                    </small>
                  </td>
                  <td>
                    <StatusPill status={t.status} />
                  </td>
                  <td>
                    <PriorityPill priority={t.priority} />
                  </td>
                  <td>
                    <span className="cell-person">
                      <Avatar name={t.assignedToName} size={22} />
                      {t.assignedToName ?? <em className="muted">Unassigned</em>}
                    </span>
                  </td>
                  <td>
                    <DueBadge dueDate={t.dueDate} status={t.status} />
                  </td>
                  <td className="muted">{timeAgo(t.updatedAt)}</td>
                  <td className="muted">{timeAgo(t.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="pagination">
          <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            <Icon name="chevronLeft" /> Previous
          </button>
          <span>
            Page {data.number + 1} of {data.totalPages}
          </span>
          <button className="btn btn-ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
            Next <Icon name="chevronRight" />
          </button>
        </div>
      )}
    </div>
  );
}
