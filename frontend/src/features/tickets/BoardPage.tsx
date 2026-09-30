import { useMemo, useState, type DragEvent } from "react";
import { useCurrentUser } from "../auth/AuthContext";
import { useDirectory } from "../../app/DirectoryContext";
import { useShell } from "../../app/ShellContext";
import { listTickets, updateTicket } from "./api";
import type { TicketListItem, TicketStatus } from "./types";
import { PRIORITIES, PRIORITY_META, STATUSES, STATUS_META, byPriorityThenAge, canTransition, isOverdue } from "./workflow";
import { TicketCard } from "./TicketCard";
import { PriorityIcon } from "./TicketBadges";
import { useBoardFilters } from "./useTicketFilters";
import { can, ROLE_SCOPE } from "../../shared/lib/permissions";
import { ALL } from "../../shared/types";
import { fullName, ticketKey } from "../../shared/lib/format";
import { useAsync, useOnTicketsChanged } from "../../shared/lib/hooks";
import { useToast } from "../../shared/ui/Toast";
import { confetti } from "../../shared/ui/confetti";
import { Icon } from "../../shared/ui/Icon";
import { AvatarStack } from "../../shared/ui/Avatar";
import { ErrorBanner, Skeleton } from "../../shared/ui/Feedback";

export function BoardPage() {
  const me = useCurrentUser();
  const toast = useToast();
  const { openCreateTicket } = useShell();
  const { projects, teams, users } = useDirectory();
  const { filters, set, togglePriority, active: filtersActive, clear, apply } = useBoardFilters();
  const editable = can.editTicket(me.role);

  const { data: tickets, setData: setTickets, error, loading, reload } = useAsync(
    () => listTickets({ size: ALL, sort: "createdAt,desc" }).then((p) => p.content),
    [],
  );
  useOnTicketsChanged(reload);

  const [dragging, setDragging] = useState<TicketListItem | null>(null);
  const [hoverColumn, setHoverColumn] = useState<TicketStatus | null>(null);

  const visible = useMemo(() => apply(tickets ?? [], me.id), [tickets, apply, me.id]);
  const columns = useMemo(() => {
    const grouped = Object.fromEntries(STATUSES.map((s) => [s, [] as TicketListItem[]])) as Record<
      TicketStatus,
      TicketListItem[]
    >;
    visible.forEach((t) => grouped[t.status].push(t));
    STATUSES.forEach((s) => grouped[s].sort(byPriorityThenAge));
    return grouped;
  }, [visible]);

  const assigneeNames = useMemo(
    () => [...new Set(visible.map((t) => t.assignedToName).filter((n): n is string => !!n))],
    [visible],
  );

  async function move(ticket: TicketListItem, to: TicketStatus, origin?: { x: number; y: number }) {
    if (!canTransition(ticket.status, to)) {
      toast.error(`${STATUS_META[ticket.status].label} can't move straight to ${STATUS_META[to].label}`);
      return;
    }
    const from = ticket.status;
    setTickets((prev) => prev?.map((t) => (t.id === ticket.id ? { ...t, status: to } : t)) ?? prev);
    try {
      const updated = await updateTicket(ticket.id, { status: to });
      setTickets(
        (prev) =>
          prev?.map((t) => (t.id === ticket.id ? { ...t, status: updated.status, updatedAt: updated.updatedAt } : t)) ??
          prev,
      );
      if (to === "CLOSED" || to === "RESOLVED") {
        confetti(origin);
        toast.success(`${ticketKey(ticket.id)} ${to === "CLOSED" ? "closed" : "resolved"}. Nice work!`);
      }
    } catch (err) {
      setTickets((prev) => prev?.map((t) => (t.id === ticket.id ? { ...t, status: from } : t)) ?? prev);
      toast.error(err);
    }
  }

  function onDragStart(e: DragEvent, ticket: TicketListItem) {
    e.dataTransfer.effectAllowed = "move";
    e.dataTransfer.setData("text/plain", String(ticket.id));
    setDragging(ticket);
  }

  function onDrop(e: DragEvent, to: TicketStatus) {
    e.preventDefault();
    setHoverColumn(null);
    if (dragging && dragging.status !== to) {
      void move(dragging, to, { x: e.clientX, y: e.clientY });
    }
    setDragging(null);
  }

  const overdue = visible.filter(isOverdue).length;
  const unassigned = visible.filter((t) => t.assignedToId === null && t.status !== "CLOSED").length;
  const critical = visible.filter((t) => t.priority === "CRITICAL" && t.status !== "CLOSED").length;

  return (
    <div className="page page-board">
      <header className="page-header">
        <div>
          <p className="eyebrow">
            <Icon name="board" size={14} /> {filters.projectId ? projects.find((p) => p.id === filters.projectId)?.name : "All projects"}
          </p>
          <h1 className="gradient-text">Board</h1>
          <p className="muted">
            {ROLE_SCOPE[me.role]}. {editable ? "Drag cards between columns to move them through the workflow." : "View only - your role can't change ticket status."}
          </p>
        </div>
        <div className="header-stats">
          <Stat label="On board" value={visible.length} />
          <Stat label="Critical" value={critical} tone="hot" />
          <Stat label="Overdue" value={overdue} tone={overdue ? "hot" : undefined} />
          <Stat label="Unassigned" value={unassigned} />
        </div>
      </header>

      <div className="filter-bar">
        <label className="search-field">
          <Icon name="search" size={16} />
          <input
            value={filters.text}
            onChange={(e) => set("q", e.target.value)}
            placeholder="Filter this board…"
          />
        </label>
        <AvatarStack names={assigneeNames} max={5} />
        <button className={`chip ${filters.mine ? "active" : ""}`} onClick={() => set("mine", filters.mine ? null : "1")}>
          <Icon name="user" size={14} /> Only mine
        </button>
        {PRIORITIES.map((p) => (
          <button
            key={p}
            className={`chip ${filters.priorities.includes(p) ? "active" : ""}`}
            onClick={() => togglePriority(p)}
          >
            <PriorityIcon priority={p} size={13} /> {PRIORITY_META[p].label}
          </button>
        ))}
        <select value={filters.projectId ?? ""} onChange={(e) => set("project", e.target.value || null)}>
          <option value="">All projects</option>
          {projects.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <select value={filters.teamId ?? ""} onChange={(e) => set("team", e.target.value || null)}>
          <option value="">All teams</option>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        <select value={filters.assignee} onChange={(e) => set("assignee", e.target.value || null)}>
          <option value="">Anyone</option>
          <option value="none">Unassigned</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {fullName(u)}
            </option>
          ))}
        </select>
        {filtersActive && (
          <button className="btn btn-ghost btn-sm" onClick={clear}>
            Clear filters
          </button>
        )}
      </div>

      {error && <ErrorBanner message={error} onRetry={reload} />}

      <div className="board">
        {STATUSES.map((status) => {
          const meta = STATUS_META[status];
          const allowed = dragging ? canTransition(dragging.status, status) : false;
          const isSource = dragging?.status === status;
          return (
            <section
              key={status}
              className={`board-column ${dragging ? (allowed ? "drop-allowed" : isSource ? "drop-source" : "drop-blocked") : ""} ${
                hoverColumn === status && allowed ? "drop-hover" : ""
              }`}
              style={{ ["--c" as string]: meta.color }}
              onDragOver={(e) => {
                if (allowed) {
                  e.preventDefault();
                  setHoverColumn(status);
                }
              }}
              onDragLeave={(e) => {
                if (!e.currentTarget.contains(e.relatedTarget as Node)) setHoverColumn(null);
              }}
              onDrop={(e) => onDrop(e, status)}
            >
              <header className="column-head">
                <span className="dot" />
                <h2>{meta.label}</h2>
                <span className="count">{columns[status].length}</span>
                {dragging && !isSource && (
                  <span className="drop-hint">{allowed ? "Drop here" : <Icon name="lock" size={13} />}</span>
                )}
              </header>
              <div className="column-body">
                {loading && !tickets
                  ? [0, 1, 2].map((i) => (
                      <div key={i} className="ticket-card skeleton-card">
                        <Skeleton height={14} />
                        <Skeleton height={14} width="60%" />
                      </div>
                    ))
                  : columns[status].map((t) => (
                      <TicketCard
                        key={t.id}
                        ticket={t}
                        draggable={editable}
                        dragging={dragging?.id === t.id}
                        onDragStart={onDragStart}
                        onDragEnd={() => {
                          setDragging(null);
                          setHoverColumn(null);
                        }}
                        onMove={move}
                      />
                    ))}
                {!loading && columns[status].length === 0 && !dragging && (
                  <p className="column-empty">{status === "CLOSED" ? "Nothing shipped yet" : "Clear skies ✨"}</p>
                )}
                {status === "OPEN" && (
                  <button className="add-card" onClick={() => openCreateTicket(filters.projectId ?? undefined)}>
                    <Icon name="plus" size={14} /> Create issue
                  </button>
                )}
              </div>
            </section>
          );
        })}
      </div>
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: "hot" }) {
  return (
    <div className={`mini-stat ${tone ?? ""}`}>
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  );
}
