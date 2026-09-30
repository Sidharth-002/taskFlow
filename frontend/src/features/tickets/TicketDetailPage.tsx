import { useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  addComment,
  deleteComment,
  deleteTicket,
  getTicket,
  listAuditLog,
  listComments,
  updateComment,
  updateTicket,
} from "./api";
import type { AuditLogResponse, CommentResponse, TicketDetail, TicketPriority, TicketStatus } from "./types";
import { PRIORITIES, PRIORITY_META, STATUSES, STATUS_META, TRANSITIONS, isOverdue } from "./workflow";
import { DueBadge, PriorityIcon, StatusPill } from "./TicketBadges";
import { useCurrentUser } from "../auth/AuthContext";
import { useDirectory } from "../../app/DirectoryContext";
import { can } from "../../shared/lib/permissions";
import { announceTicketsChanged, useAsync } from "../../shared/lib/hooks";
import { fromDateInput, fullDate, fullName, ticketKey, timeAgo, toDateInput } from "../../shared/lib/format";
import { useToast } from "../../shared/ui/Toast";
import { confetti } from "../../shared/ui/confetti";
import { Avatar } from "../../shared/ui/Avatar";
import { Icon, type IconName } from "../../shared/ui/Icon";
import { ConfirmModal } from "../../shared/ui/Modal";
import { EmptyState, ErrorBanner, Spinner } from "../../shared/ui/Feedback";

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const ticketId = Number(id);
  const me = useCurrentUser();
  const toast = useToast();
  const navigate = useNavigate();
  const { projects, teams, users, userName } = useDirectory();

  const { data, setData, error, loading, reload } = useAsync(
    () => Promise.all([getTicket(ticketId), listComments(ticketId)]),
    [ticketId],
  );
  const activity = useAsync(() => listAuditLog(ticketId), [ticketId]);

  const [tab, setTab] = useState<"comments" | "activity">("comments");
  const [editingTitle, setEditingTitle] = useState(false);
  const [titleDraft, setTitleDraft] = useState("");
  const [editingDescription, setEditingDescription] = useState(false);
  const [descriptionDraft, setDescriptionDraft] = useState("");
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  if (loading && !data) return <Spinner />;
  if (error && !data) {
    return (
      <div className="page">
        <ErrorBanner message={error} onRetry={reload} />
        <Link to="/board" className="btn btn-ghost">
          <Icon name="chevronLeft" /> Back to board
        </Link>
      </div>
    );
  }
  if (!data) return null;

  const [ticket, comments] = data;
  const editable = can.editTicket(me.role);
  const canAssign = can.assignTicket(me.role);
  const project = projects.find((p) => p.id === ticket.projectId);
  const team = teams.find((t) => t.id === ticket.teamId);

  const setTicket = (t: TicketDetail) => setData([t, comments]);
  const setComments = (c: CommentResponse[]) => setData([ticket, c]);

  async function patch(input: Parameters<typeof updateTicket>[1], successMessage?: string) {
    setSaving(true);
    try {
      const updated = await updateTicket(ticket.id, input);
      setTicket(updated);
      announceTicketsChanged();
      void activity.reload();
      if (successMessage) toast.success(successMessage);
      return updated;
    } catch (err) {
      toast.error(err);
      return null;
    } finally {
      setSaving(false);
    }
  }

  async function transition(to: TicketStatus, e: React.MouseEvent) {
    const origin = { x: e.clientX, y: e.clientY };
    const updated = await patch({ status: to }, `Moved to ${STATUS_META[to].label}`);
    if (updated && (to === "CLOSED" || to === "RESOLVED")) confetti(origin);
  }

  async function saveTitle() {
    const title = titleDraft.trim();
    setEditingTitle(false);
    if (title && title !== ticket.title) await patch({ title });
  }

  async function saveDescription() {
    setEditingDescription(false);
    if (descriptionDraft !== (ticket.description ?? "")) await patch({ description: descriptionDraft });
  }

  async function handleDelete() {
    try {
      await deleteTicket(ticket.id);
      announceTicketsChanged();
      toast.success(`${ticketKey(ticket.id)} deleted`);
      navigate("/board");
    } catch (err) {
      toast.error(err);
      setConfirmDelete(false);
    }
  }

  const stepIndex = STATUSES.indexOf(ticket.status);

  return (
    <div className="page page-detail">
      <nav className="breadcrumbs">
        <Link to="/projects">Projects</Link>
        <Icon name="chevronRight" size={14} />
        <Link to={`/board?project=${ticket.projectId}`}>{project?.name ?? "Project"}</Link>
        <Icon name="chevronRight" size={14} />
        <span className="mono">{ticketKey(ticket.id)}</span>
      </nav>

      <div className="detail-grid">
        <section className="detail-main">
          {editingTitle ? (
            <input
              className="title-input"
              autoFocus
              value={titleDraft}
              maxLength={255}
              onChange={(e) => setTitleDraft(e.target.value)}
              onBlur={saveTitle}
              onKeyDown={(e) => {
                if (e.key === "Enter") void saveTitle();
                if (e.key === "Escape") setEditingTitle(false);
              }}
            />
          ) : (
            <h1
              className={`detail-title ${editable ? "editable" : ""}`}
              onClick={() => {
                if (!editable) return;
                setTitleDraft(ticket.title);
                setEditingTitle(true);
              }}
              title={editable ? "Click to edit" : undefined}
            >
              {ticket.title}
            </h1>
          )}

          <div className="stepper" aria-label="Workflow progress">
            {STATUSES.map((s, i) => (
              <div
                key={s}
                className={`step ${i < stepIndex ? "done" : ""} ${i === stepIndex ? "current" : ""}`}
                style={{ ["--c" as string]: STATUS_META[s].color }}
              >
                <span className="step-dot">{i < stepIndex ? <Icon name="check" size={12} /> : i + 1}</span>
                <span className="step-label">{STATUS_META[s].label}</span>
              </div>
            ))}
          </div>

          {editable && TRANSITIONS[ticket.status].length > 0 && (
            <div className="transitions">
              {TRANSITIONS[ticket.status].map((to) => (
                <button
                  key={to}
                  className="btn btn-transition"
                  style={{ ["--c" as string]: STATUS_META[to].color }}
                  disabled={saving}
                  onClick={(e) => transition(to, e)}
                >
                  {STATUS_META[to].verb} <Icon name="arrowRight" size={14} />
                </button>
              ))}
            </div>
          )}

          <div className="panel description-panel">
            <header className="panel-head">
              <h3>Description</h3>
              {editable && !editingDescription && (
                <button
                  className="btn btn-ghost btn-sm"
                  onClick={() => {
                    setDescriptionDraft(ticket.description ?? "");
                    setEditingDescription(true);
                  }}
                >
                  <Icon name="edit" size={14} /> Edit
                </button>
              )}
            </header>
            {editingDescription ? (
              <div className="stack">
                <textarea
                  autoFocus
                  rows={8}
                  value={descriptionDraft}
                  onChange={(e) => setDescriptionDraft(e.target.value)}
                />
                <div className="row-end">
                  <button className="btn btn-ghost" onClick={() => setEditingDescription(false)}>
                    Cancel
                  </button>
                  <button className="btn btn-primary" onClick={saveDescription}>
                    Save
                  </button>
                </div>
              </div>
            ) : ticket.description ? (
              <p className="prose">{ticket.description}</p>
            ) : (
              <p className="muted">No description yet.</p>
            )}
          </div>

          <div className="tabs">
            <button className={tab === "comments" ? "active" : ""} onClick={() => setTab("comments")}>
              <Icon name="message" size={15} /> Comments <span className="count">{comments.length}</span>
            </button>
            <button className={tab === "activity" ? "active" : ""} onClick={() => setTab("activity")}>
              <Icon name="activity" size={15} /> Activity
            </button>
          </div>

          {tab === "comments" ? (
            <Comments ticketId={ticket.id} comments={comments} onChange={setComments} />
          ) : (
            <ActivityFeed
              entries={activity.data}
              loading={activity.loading}
              error={activity.error}
              userName={userName}
            />
          )}
        </section>

        <aside className="detail-side">
          <div className="panel side-panel">
            <div className="side-status">
              <StatusPill status={ticket.status} />
              {isOverdue(ticket) && (
                <span className="pill pill-hot">
                  <Icon name="flame" size={12} /> Overdue
                </span>
              )}
            </div>

            <SideField label="Assignee">
              {canAssign ? (
                <select
                  value={ticket.assignedToId ?? ""}
                  disabled={saving}
                  onChange={(e) => {
                    const user = users.find((u) => u.id === Number(e.target.value));
                    if (user) void patch({ assignedToId: user.id }, `Assigned to ${fullName(user)}`);
                  }}
                >
                  {ticket.assignedToId === null && <option value="">Unassigned</option>}
                  {users
                    .filter((u) => u.active || u.id === ticket.assignedToId)
                    .map((u) => (
                      <option key={u.id} value={u.id}>
                        {fullName(u)}
                        {u.id === me.id ? " (you)" : ""}
                      </option>
                    ))}
                </select>
              ) : (
                <Person name={userName(ticket.assignedToId)} />
              )}
              {canAssign && ticket.assignedToId !== me.id && (
                <button className="link-btn" onClick={() => patch({ assignedToId: me.id }, "Assigned to you")}>
                  Assign to me
                </button>
              )}
            </SideField>

            <SideField label="Team">
              {canAssign ? (
                <select
                  value={ticket.teamId ?? ""}
                  disabled={saving}
                  onChange={(e) => {
                    const t = teams.find((x) => x.id === Number(e.target.value));
                    if (t) void patch({ teamId: t.id }, `Moved to ${t.name}`);
                  }}
                >
                  {ticket.teamId === null && <option value="">No team</option>}
                  {teams
                    .filter((t) => t.active || t.id === ticket.teamId)
                    .map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                </select>
              ) : (
                <span>{team?.name ?? <em className="muted">No team</em>}</span>
              )}
            </SideField>

            <SideField label="Priority">
              {editable ? (
                <select
                  value={ticket.priority}
                  disabled={saving}
                  onChange={(e) => patch({ priority: e.target.value as TicketPriority })}
                >
                  {PRIORITIES.map((p) => (
                    <option key={p} value={p}>
                      {PRIORITY_META[p].label}
                    </option>
                  ))}
                </select>
              ) : (
                <span className="inline-flex">
                  <PriorityIcon priority={ticket.priority} /> {PRIORITY_META[ticket.priority].label}
                </span>
              )}
            </SideField>

            <SideField label="Due date">
              {editable ? (
                <input
                  type="date"
                  value={toDateInput(ticket.dueDate)}
                  disabled={saving}
                  onChange={(e) => {
                    const dueDate = fromDateInput(e.target.value);
                    if (dueDate) void patch({ dueDate }, "Due date updated");
                  }}
                />
              ) : (
                <DueBadge dueDate={ticket.dueDate} status={ticket.status} />
              )}
            </SideField>

            <SideField label="Reporter">
              <Person name={userName(ticket.createdById)} />
            </SideField>

            <SideField label="Project">
              <Link to={`/board?project=${ticket.projectId}`}>{project?.name ?? `#${ticket.projectId}`}</Link>
            </SideField>

            <div className="side-meta">
              <span>Created {fullDate(ticket.createdAt)}</span>
              <span>Updated {timeAgo(ticket.updatedAt)}</span>
              <span className="mono">v{ticket.version}</span>
            </div>
          </div>

          {can.deleteTicket(me.role) && (
            <button className="btn btn-danger-ghost" onClick={() => setConfirmDelete(true)}>
              <Icon name="trash" size={15} /> Delete issue
            </button>
          )}
        </aside>
      </div>

      {confirmDelete && (
        <ConfirmModal
          title={`Delete ${ticketKey(ticket.id)}?`}
          message="This permanently removes the issue and its comments. This can't be undone."
          confirmLabel="Delete issue"
          onConfirm={handleDelete}
          onClose={() => setConfirmDelete(false)}
        />
      )}
    </div>
  );
}

function SideField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="side-field">
      <span className="side-label">{label}</span>
      <div className="side-value">{children}</div>
    </div>
  );
}

function Person({ name }: { name: string | null }) {
  return (
    <span className="cell-person">
      <Avatar name={name} size={24} />
      {name ?? <em className="muted">Unassigned</em>}
    </span>
  );
}

function Comments({
  ticketId,
  comments,
  onChange,
}: {
  ticketId: number;
  comments: CommentResponse[];
  onChange: (c: CommentResponse[]) => void;
}) {
  const me = useCurrentUser();
  const toast = useToast();
  const [draft, setDraft] = useState("");
  const [posting, setPosting] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState("");

  async function post(e?: FormEvent) {
    e?.preventDefault();
    if (!draft.trim()) return;
    setPosting(true);
    try {
      const comment = await addComment(ticketId, draft.trim());
      onChange([...comments, comment]);
      setDraft("");
    } catch (err) {
      toast.error(err);
    } finally {
      setPosting(false);
    }
  }

  async function saveEdit(id: number) {
    try {
      const updated = await updateComment(id, editDraft.trim());
      onChange(comments.map((c) => (c.id === id ? updated : c)));
      setEditingId(null);
    } catch (err) {
      toast.error(err);
    }
  }

  async function remove(id: number) {
    try {
      await deleteComment(id);
      onChange(comments.filter((c) => c.id !== id));
    } catch (err) {
      toast.error(err);
    }
  }

  return (
    <div className="comments">
      <form className="comment-composer" onSubmit={post}>
        <Avatar name={fullName(me)} size={34} />
        <div className="composer-box">
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) void post();
            }}
            placeholder="Add a comment…  (Ctrl+Enter to send)"
            rows={3}
            maxLength={5000}
          />
          <div className="row-end">
            <button className="btn btn-primary btn-sm" type="submit" disabled={posting || !draft.trim()}>
              {posting ? "Sending…" : "Comment"}
            </button>
          </div>
        </div>
      </form>

      {comments.length === 0 && (
        <EmptyState icon="message" title="No comments yet">
          Start the conversation.
        </EmptyState>
      )}

      <ul className="comment-list">
        {[...comments].reverse().map((c) => {
          const mine = c.authorId === me.id;
          return (
            <li key={c.id} className="comment">
              <Avatar name={c.authorName} size={34} />
              <div className="comment-body">
                <header>
                  <strong>{c.authorName}</strong>
                  <span className="muted" title={fullDate(c.createdAt)}>
                    {timeAgo(c.createdAt)}
                    {c.updatedAt !== c.createdAt && " · edited"}
                  </span>
                  <span className="spacer" />
                  {mine && editingId !== c.id && (
                    <button
                      className="icon-btn icon-btn-sm"
                      aria-label="Edit comment"
                      onClick={() => {
                        setEditingId(c.id);
                        setEditDraft(c.body);
                      }}
                    >
                      <Icon name="edit" size={14} />
                    </button>
                  )}
                  {(mine || me.role === "ORG_ADMIN") && (
                    <button className="icon-btn icon-btn-sm" aria-label="Delete comment" onClick={() => remove(c.id)}>
                      <Icon name="trash" size={14} />
                    </button>
                  )}
                </header>
                {editingId === c.id ? (
                  <div className="stack">
                    <textarea rows={3} value={editDraft} onChange={(e) => setEditDraft(e.target.value)} />
                    <div className="row-end">
                      <button className="btn btn-ghost btn-sm" onClick={() => setEditingId(null)}>
                        Cancel
                      </button>
                      <button className="btn btn-primary btn-sm" onClick={() => saveEdit(c.id)} disabled={!editDraft.trim()}>
                        Save
                      </button>
                    </div>
                  </div>
                ) : (
                  <p className="prose">{c.body}</p>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

const EVENT_ICONS: Record<string, IconName> = {
  TICKET_CREATED: "sparkles",
  TICKET_ASSIGNED: "user",
  TICKET_STATUS_CHANGED: "arrowRight",
  TICKET_CLOSED: "check",
  TICKET_COMMENT_ADDED: "message",
  TICKET_OVERDUE: "flame",
};

function humanizeSummary(summary: string, userName: (id: number | null) => string | null): string {
  return summary
    .replace(/user #(\d+)/g, (match, id) => userName(Number(id)) ?? match)
    .replace(/\b(OPEN|IN_PROGRESS|WAITING|RESOLVED|CLOSED)\b/g, (status) => STATUS_META[status as TicketStatus].label);
}

function ActivityFeed({
  entries,
  loading,
  error,
  userName,
}: {
  entries: AuditLogResponse[] | null;
  loading: boolean;
  error: string | null;
  userName: (id: number | null) => string | null;
}) {
  if (loading && !entries) return <Spinner />;
  if (error) return <ErrorBanner message={error} />;
  if (!entries || entries.length === 0) {
    return (
      <EmptyState icon="activity" title="No activity recorded yet">
        Events arrive via Kafka a moment after each change.
      </EmptyState>
    );
  }
  return (
    <ol className="timeline">
      {[...entries]
        .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
        .map((entry) => (
          <li key={entry.id} className={`timeline-item ev-${entry.eventType.toLowerCase()}`}>
            <span className="timeline-icon">
              <Icon name={EVENT_ICONS[entry.eventType] ?? "activity"} size={14} />
            </span>
            <div>
              <p>{humanizeSummary(entry.summary, userName)}</p>
              <span className="muted" title={fullDate(entry.occurredAt)}>
                {entry.actorUserId !== null ? `${userName(entry.actorUserId) ?? "Someone"} · ` : "FlowDesk · "}
                {timeAgo(entry.occurredAt)}
              </span>
            </div>
          </li>
        ))}
    </ol>
  );
}
