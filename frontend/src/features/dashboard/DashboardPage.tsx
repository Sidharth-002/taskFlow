import { useMemo } from "react";
import { Link } from "react-router-dom";
import { useCurrentUser } from "../auth/AuthContext";
import { useShell } from "../../app/ShellContext";
import { useDirectory } from "../../app/DirectoryContext";
import { getDashboardSummary, type DashboardSummary } from "./api";
import { listTickets } from "../tickets/api";
import type { TicketListItem } from "../tickets/types";
import { PRIORITIES, PRIORITY_META, STATUSES, STATUS_META, byPriorityThenAge, isDone, isOverdue } from "../tickets/workflow";
import { DueBadge, PriorityIcon, StatusPill } from "../tickets/TicketBadges";
import { BarList, Donut, Versus } from "./Charts";
import { can, ROLE_SCOPE } from "../../shared/lib/permissions";
import { ALL } from "../../shared/types";
import { greeting, ticketKey } from "../../shared/lib/format";
import { useAsync, useCountUp, useOnTicketsChanged } from "../../shared/lib/hooks";
import { Icon, type IconName } from "../../shared/ui/Icon";
import { Avatar } from "../../shared/ui/Avatar";
import { EmptyState, ErrorBanner, Skeleton } from "../../shared/ui/Feedback";

const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

/** The same shape the backend's /api/dashboard/summary returns, computed from the tickets this user can see. */
function summarize(tickets: TicketListItem[]): DashboardSummary {
  const weekAgo = Date.now() - WEEK_MS;
  const countsByStatus: DashboardSummary["countsByStatus"] = {};
  const countsByPriority: DashboardSummary["countsByPriority"] = {};
  tickets.forEach((t) => {
    countsByStatus[t.status] = (countsByStatus[t.status] ?? 0) + 1;
    countsByPriority[t.priority] = (countsByPriority[t.priority] ?? 0) + 1;
  });
  return {
    totalTickets: tickets.length,
    countsByStatus,
    countsByPriority,
    unassignedCount: tickets.filter((t) => t.assignedToId === null && !isDone(t.status)).length,
    overdueCount: tickets.filter(isOverdue).length,
    createdLastSevenDays: tickets.filter((t) => new Date(t.createdAt).getTime() >= weekAgo).length,
    closedLastSevenDays: tickets.filter((t) => t.status === "CLOSED" && new Date(t.updatedAt).getTime() >= weekAgo)
      .length,
  };
}

export function DashboardPage() {
  const me = useCurrentUser();
  const { openCreateTicket } = useShell();
  const { teams } = useDirectory();
  const hasServerSummary = can.viewDashboardSummary(me.role);

  const { data, error, loading, reload } = useAsync(
    () =>
      Promise.all([
        listTickets({ size: ALL, sort: "createdAt,desc" }).then((p) => p.content),
        hasServerSummary ? getDashboardSummary() : Promise.resolve(null),
      ]),
    [hasServerSummary],
  );
  useOnTicketsChanged(reload);

  const tickets = useMemo(() => data?.[0] ?? [], [data]);
  const summary = data ? (data[1] ?? summarize(tickets)) : null;

  const myWork = useMemo(
    () => tickets.filter((t) => t.assignedToId === me.id && !isDone(t.status)).sort(byPriorityThenAge).slice(0, 6),
    [tickets, me.id],
  );
  const onFire = useMemo(
    () =>
      tickets
        .filter((t) => !isDone(t.status) && (isOverdue(t) || t.priority === "CRITICAL"))
        .sort(byPriorityThenAge)
        .slice(0, 6),
    [tickets],
  );
  const workload = useMemo(() => {
    const counts = new Map<string, number>();
    tickets
      .filter((t) => !isDone(t.status))
      .forEach((t) => counts.set(t.assignedToName ?? "Unassigned", (counts.get(t.assignedToName ?? "Unassigned") ?? 0) + 1));
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 6)
      .map(([name, value], i) => ({
        key: name,
        label: name,
        value,
        color: name === "Unassigned" ? "var(--text-faint)" : `var(--series-${(i % 5) + 1})`,
      }));
  }, [tickets]);
  const teamLoad = useMemo(
    () =>
      teams
        .filter((t) => t.active)
        .map((team, i) => ({
          key: String(team.id),
          label: team.name,
          value: tickets.filter((t) => t.teamId === team.id && !isDone(t.status)).length,
          color: `var(--series-${(i % 5) + 1})`,
        }))
        .sort((a, b) => b.value - a.value)
        .slice(0, 6),
    [teams, tickets],
  );

  const open = summary ? summary.totalTickets - (summary.countsByStatus.CLOSED ?? 0) : 0;
  const done = summary ? (summary.countsByStatus.RESOLVED ?? 0) + (summary.countsByStatus.CLOSED ?? 0) : 0;
  const completion = summary && summary.totalTickets > 0 ? Math.round((done / summary.totalTickets) * 100) : 0;

  return (
    <div className="page">
      <header className="page-header hero">
        <div>
          <p className="eyebrow">
            <Icon name="sparkles" size={14} /> {ROLE_SCOPE[me.role]}
          </p>
          <h1>
            {greeting()}, <span className="gradient-text">{me.firstName}</span>
          </h1>
          <p className="muted">
            {summary
              ? `${open} open · ${summary.overdueCount} overdue · ${completion}% of everything is done`
              : "Pulling the latest numbers…"}
          </p>
        </div>
        <div className="row">
          <Link to="/board" className="btn btn-ghost">
            <Icon name="board" /> Open board
          </Link>
          <button className="btn btn-primary" onClick={() => openCreateTicket()}>
            <Icon name="plus" /> Create issue
          </button>
        </div>
      </header>

      {error && <ErrorBanner message={error} onRetry={reload} />}

      <div className="stat-grid">
        <StatCard icon="inbox" label="Open issues" value={open} tone="violet" loading={!summary} />
        <StatCard icon="flame" label="Overdue" value={summary?.overdueCount ?? 0} tone="red" loading={!summary} />
        <StatCard icon="user" label="Unassigned" value={summary?.unassignedCount ?? 0} tone="amber" loading={!summary} />
        <StatCard icon="zap" label="Created this week" value={summary?.createdLastSevenDays ?? 0} tone="cyan" loading={!summary} />
        <StatCard icon="check" label="Closed this week" value={summary?.closedLastSevenDays ?? 0} tone="green" loading={!summary} />
      </div>

      <div className="dash-grid">
        <section className="panel">
          <header className="panel-head">
            <h3>Status breakdown</h3>
            <Link to="/board" className="link-btn">
              Board <Icon name="arrowRight" size={14} />
            </Link>
          </header>
          {summary ? (
            <Donut
              label="Tickets by status"
              slices={STATUSES.map((s) => ({
                key: s,
                label: STATUS_META[s].label,
                value: summary.countsByStatus[s] ?? 0,
                color: STATUS_META[s].color,
              }))}
            />
          ) : (
            <Skeleton height={200} />
          )}
        </section>

        <section className="panel">
          <header className="panel-head">
            <h3>By priority</h3>
          </header>
          {summary ? (
            <BarList
              rows={PRIORITIES.map((p) => ({
                key: p,
                label: PRIORITY_META[p].label,
                value: summary.countsByPriority[p] ?? 0,
                color: PRIORITY_META[p].color,
              }))}
            />
          ) : (
            <Skeleton height={140} />
          )}
          <header className="panel-head spaced">
            <h3>This week</h3>
          </header>
          {summary && (
            <Versus
              left={{ label: "created", value: summary.createdLastSevenDays, color: "var(--series-2)" }}
              right={{ label: "closed", value: summary.closedLastSevenDays, color: "var(--status-resolved)" }}
            />
          )}
        </section>

        <section className="panel">
          <header className="panel-head">
            <h3>Who's carrying the load</h3>
            <span className="muted small">open issues</span>
          </header>
          <BarList rows={workload} emptyText="Nothing open - enjoy it." />
          {teamLoad.length > 0 && (
            <>
              <header className="panel-head spaced">
                <h3>Team load</h3>
                <Link to="/teams" className="link-btn">
                  Teams <Icon name="arrowRight" size={14} />
                </Link>
              </header>
              <BarList rows={teamLoad} />
            </>
          )}
        </section>
      </div>

      <div className="dash-grid two">
        <TicketListPanel
          title="My work"
          icon="user"
          tickets={myWork}
          loading={loading && !data}
          empty="Nothing assigned to you. Suspiciously calm."
        />
        <TicketListPanel
          title="On fire"
          icon="flame"
          tickets={onFire}
          loading={loading && !data}
          empty="No critical or overdue issues. 🧯"
          hot
        />
      </div>
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
  tone,
  loading,
}: {
  icon: IconName;
  label: string;
  value: number;
  tone: "violet" | "red" | "amber" | "cyan" | "green";
  loading: boolean;
}) {
  const shown = useCountUp(loading ? 0 : value);
  return (
    <div className={`stat-card tone-${tone}`}>
      <span className="stat-icon">
        <Icon name={icon} size={20} />
      </span>
      <strong className="stat-value">{loading ? "–" : shown}</strong>
      <span className="stat-label">{label}</span>
    </div>
  );
}

function TicketListPanel({
  title,
  icon,
  tickets,
  loading,
  empty,
  hot,
}: {
  title: string;
  icon: IconName;
  tickets: TicketListItem[];
  loading: boolean;
  empty: string;
  hot?: boolean;
}) {
  return (
    <section className={`panel ${hot ? "panel-hot" : ""}`}>
      <header className="panel-head">
        <h3>
          <Icon name={icon} size={16} /> {title}
        </h3>
        <span className="count">{tickets.length}</span>
      </header>
      {loading ? (
        <Skeleton height={120} />
      ) : tickets.length === 0 ? (
        <EmptyState icon={icon} title={empty} />
      ) : (
        <ul className="ticket-rows">
          {tickets.map((t) => (
            <li key={t.id}>
              <Link to={`/tickets/${t.id}`} className="ticket-row">
                <PriorityIcon priority={t.priority} />
                <span className="mono muted">{ticketKey(t.id)}</span>
                <span className="ticket-row-title">{t.title}</span>
                <DueBadge dueDate={t.dueDate} status={t.status} />
                <StatusPill status={t.status} />
                <Avatar name={t.assignedToName} size={22} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
