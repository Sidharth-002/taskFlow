import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useCurrentUser } from "../auth/AuthContext";
import { useDirectory } from "../../app/DirectoryContext";
import {
  addTeamMember,
  assignTeamLead,
  createTeam,
  deactivateTeam,
  listTeamMembers,
  removeTeamMember,
  updateTeam,
  type TeamResponse,
} from "./api";
import { listTickets } from "../tickets/api";
import { STATUSES, STATUS_META, isDone, isOverdue } from "../tickets/workflow";
import type { TicketListItem } from "../tickets/types";
import { can } from "../../shared/lib/permissions";
import { ALL, type UserSummary } from "../../shared/types";
import { fullName, shortDate } from "../../shared/lib/format";
import { useAsync, useOnTicketsChanged } from "../../shared/lib/hooks";
import { useToast } from "../../shared/ui/Toast";
import { Modal, ConfirmModal } from "../../shared/ui/Modal";
import { Avatar, AvatarStack } from "../../shared/ui/Avatar";
import { RolePill } from "../../shared/ui/RolePill";
import { Icon } from "../../shared/ui/Icon";
import { EmptyState, Spinner } from "../../shared/ui/Feedback";

export function TeamsPage() {
  const me = useCurrentUser();
  const { teams, ready, reloadTeams } = useDirectory();
  const [params, setParams] = useSearchParams();
  const [editing, setEditing] = useState<TeamResponse | "new" | null>(null);
  const isAdmin = can.manageOrganization(me.role);

  const tickets = useAsync(() => listTickets({ size: ALL }).then((p) => p.content), []);
  useOnTicketsChanged(tickets.reload);

  const selectedId = params.get("team") ? Number(params.get("team")) : (teams.find((t) => t.active)?.id ?? teams[0]?.id);
  const selected = teams.find((t) => t.id === selectedId) ?? null;

  const openByTeam = useMemo(() => {
    const counts = new Map<number, number>();
    (tickets.data ?? []).forEach((t) => {
      if (t.teamId !== null && !isDone(t.status)) counts.set(t.teamId, (counts.get(t.teamId) ?? 0) + 1);
    });
    return counts;
  }, [tickets.data]);

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">
            <Icon name="team" size={14} /> {teams.filter((t) => t.active).length} active teams
          </p>
          <h1 className="gradient-text">Teams</h1>
          <p className="muted">Group people, give them a lead, and route issues to them.</p>
        </div>
        {isAdmin && (
          <button className="btn btn-primary" onClick={() => setEditing("new")}>
            <Icon name="plus" /> New team
          </button>
        )}
      </header>

      {!ready ? (
        <Spinner />
      ) : teams.length === 0 ? (
        <EmptyState
          icon="team"
          title="No teams yet"
          action={
            isAdmin && (
              <button className="btn btn-primary" onClick={() => setEditing("new")}>
                <Icon name="plus" /> Create your first team
              </button>
            )
          }
        >
          Teams let a lead see and triage everything routed to them.
        </EmptyState>
      ) : (
        <div className="master-detail">
          <ul className="team-list">
            {teams.map((team) => (
              <li key={team.id}>
                <button
                  className={`team-list-item ${team.id === selected?.id ? "active" : ""} ${team.active ? "" : "inactive"}`}
                  onClick={() => setParams({ team: String(team.id) }, { replace: true })}
                >
                  <TeamGlyph name={team.name} />
                  <span className="team-list-text">
                    <strong>{team.name}</strong>
                    <small className="muted">
                      {team.active ? (team.teamLeadName ? `Led by ${team.teamLeadName}` : "No lead yet") : "Deactivated"}
                    </small>
                  </span>
                  <span className="count" title="Open issues">
                    {openByTeam.get(team.id) ?? 0}
                  </span>
                </button>
              </li>
            ))}
          </ul>

          {selected && (
            <TeamDetail
              key={selected.id}
              team={selected}
              tickets={(tickets.data ?? []).filter((t) => t.teamId === selected.id)}
              onEdit={() => setEditing(selected)}
              onChanged={reloadTeams}
            />
          )}
        </div>
      )}

      {editing && (
        <TeamFormModal
          team={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={async (team) => {
            await reloadTeams();
            setParams({ team: String(team.id) }, { replace: true });
            setEditing(null);
          }}
        />
      )}
    </div>
  );
}

function TeamGlyph({ name, size = 40 }: { name: string; size?: number }) {
  return (
    <span className="team-glyph" style={{ width: size, height: size }}>
      <Avatar name={name} size={size} />
    </span>
  );
}

function TeamDetail({
  team,
  tickets,
  onEdit,
  onChanged,
}: {
  team: TeamResponse;
  tickets: TicketListItem[];
  onEdit: () => void;
  onChanged: () => Promise<void>;
}) {
  const me = useCurrentUser();
  const toast = useToast();
  const { users } = useDirectory();
  const isAdmin = can.manageOrganization(me.role);
  const canManageMembers = isAdmin || (me.role === "TEAM_LEAD" && team.teamLeadId === me.id);

  const [members, setMembers] = useState<UserSummary[] | null>(null);
  const [adding, setAdding] = useState<number | "">("");
  const [busy, setBusy] = useState(false);
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);

  useEffect(() => {
    listTeamMembers(team.id)
      .then(setMembers)
      .catch((err) => {
        toast.error(err);
        setMembers([]);
      });
  }, [team.id, toast]);

  const memberIds = new Set((members ?? []).map((m) => m.id));
  const candidates = users.filter((u) => u.active && !memberIds.has(u.id));
  const open = tickets.filter((t) => !isDone(t.status));
  const overdue = tickets.filter(isOverdue).length;

  async function run<T>(action: () => Promise<T>, message: string) {
    setBusy(true);
    try {
      const result = await action();
      toast.success(message);
      return result;
    } catch (err) {
      toast.error(err);
      return undefined;
    } finally {
      setBusy(false);
    }
  }

  async function add() {
    if (adding === "") return;
    const user = users.find((u) => u.id === adding);
    const updated = await run(() => addTeamMember(team.id, adding), `${user ? fullName(user) : "Member"} joined ${team.name}`);
    if (updated) {
      setMembers(updated);
      setAdding("");
    }
  }

  async function remove(user: UserSummary) {
    const ok = await run(() => removeTeamMember(team.id, user.id).then(() => true), `${fullName(user)} left ${team.name}`);
    if (ok) setMembers((prev) => prev?.filter((m) => m.id !== user.id) ?? prev);
  }

  async function makeLead(user: UserSummary) {
    const ok = await run(() => assignTeamLead(team.id, user.id), `${fullName(user)} now leads ${team.name}`);
    if (ok) await onChanged();
  }

  async function deactivate() {
    const ok = await run(() => deactivateTeam(team.id), `${team.name} deactivated`);
    setConfirmDeactivate(false);
    if (ok) await onChanged();
  }

  return (
    <section className="panel team-detail">
      <header className="team-hero">
        <TeamGlyph name={team.name} size={64} />
        <div>
          <h2>
            {team.name} {!team.active && <span className="pill">Deactivated</span>}
          </h2>
          <p className="muted">{team.description || "No description."}</p>
          <p className="muted small">Created {shortDate(team.createdAt)}</p>
        </div>
        <span className="spacer" />
        {isAdmin && team.active && (
          <div className="row">
            <button className="btn btn-ghost btn-sm" onClick={onEdit}>
              <Icon name="edit" size={14} /> Edit
            </button>
            <button className="btn btn-danger-ghost btn-sm" onClick={() => setConfirmDeactivate(true)}>
              <Icon name="archive" size={14} /> Deactivate
            </button>
          </div>
        )}
      </header>

      <div className="team-stats">
        <div className="mini-stat">
          <strong>{members?.length ?? "–"}</strong>
          <span>members</span>
        </div>
        <div className="mini-stat">
          <strong>{open.length}</strong>
          <span>open issues</span>
        </div>
        <div className={`mini-stat ${overdue ? "hot" : ""}`}>
          <strong>{overdue}</strong>
          <span>overdue</span>
        </div>
        <Link to={`/board?team=${team.id}`} className="btn btn-secondary btn-sm">
          <Icon name="board" size={14} /> Team board
        </Link>
      </div>

      <div className="status-strip" aria-label="Open work by status">
        {STATUSES.map((s) => {
          const n = tickets.filter((t) => t.status === s).length;
          return n > 0 ? (
            <span key={s} style={{ flex: n, background: STATUS_META[s].color }} title={`${STATUS_META[s].label}: ${n}`} />
          ) : null;
        })}
      </div>

      <div className="lead-card">
        <Icon name="crown" className="crown" />
        {team.teamLeadId ? (
          <>
            <Avatar name={team.teamLeadName} size={36} />
            <div>
              <strong>{team.teamLeadName}</strong>
              <small className="muted">Team lead</small>
            </div>
          </>
        ) : (
          <span className="muted">No lead assigned - pick one from the members below.</span>
        )}
      </div>

      <header className="panel-head spaced">
        <h3>Members</h3>
        {members && <AvatarStack names={members.map(fullName)} max={6} />}
      </header>

      {members === null ? (
        <Spinner />
      ) : members.length === 0 ? (
        <p className="muted">No members yet.</p>
      ) : (
        <ul className="member-list">
          {members.map((m) => (
            <li key={m.id} className="member">
              <Avatar name={fullName(m)} size={36} />
              <div className="member-text">
                <strong>
                  {fullName(m)} {m.id === team.teamLeadId && <Icon name="crown" size={13} className="crown" />}
                </strong>
                <small className="muted">{m.email}</small>
              </div>
              <RolePill role={m.role} />
              <span className="spacer" />
              {isAdmin && team.active && m.id !== team.teamLeadId && (
                <button className="btn btn-ghost btn-sm" disabled={busy} onClick={() => makeLead(m)}>
                  <Icon name="crown" size={14} /> Make lead
                </button>
              )}
              {canManageMembers && team.active && (
                <button
                  className="icon-btn icon-btn-sm"
                  aria-label={`Remove ${fullName(m)}`}
                  disabled={busy}
                  onClick={() => remove(m)}
                >
                  <Icon name="x" size={14} />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {canManageMembers && team.active && (
        <div className="add-member">
          <select value={adding} onChange={(e) => setAdding(e.target.value ? Number(e.target.value) : "")}>
            <option value="">Add someone to {team.name}…</option>
            {candidates.map((u) => (
              <option key={u.id} value={u.id}>
                {fullName(u)} · {u.email}
              </option>
            ))}
          </select>
          <button className="btn btn-primary" disabled={adding === "" || busy} onClick={add}>
            <Icon name="plus" /> Add
          </button>
        </div>
      )}

      {confirmDeactivate && (
        <ConfirmModal
          title={`Deactivate ${team.name}?`}
          message="The team keeps its history, but can no longer be picked for new issues."
          confirmLabel="Deactivate"
          busy={busy}
          onConfirm={deactivate}
          onClose={() => setConfirmDeactivate(false)}
        />
      )}
    </section>
  );
}

function TeamFormModal({
  team,
  onClose,
  onSaved,
}: {
  team: TeamResponse | null;
  onClose: () => void;
  onSaved: (team: TeamResponse) => Promise<void>;
}) {
  const toast = useToast();
  const [name, setName] = useState(team?.name ?? "");
  const [description, setDescription] = useState(team?.description ?? "");
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      const input = { name: name.trim(), description: description.trim() || undefined };
      const saved = team ? await updateTeam(team.id, input) : await createTeam(input);
      toast.success(team ? "Team updated" : `${saved.name} created`);
      await onSaved(saved);
    } catch (err) {
      toast.error(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title={team ? `Edit ${team.name}` : "New team"}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" type="submit" form="team-form" disabled={saving || !name.trim()}>
            {saving ? "Saving…" : team ? "Save" : "Create team"}
          </button>
        </>
      }
    >
      <form id="team-form" className="stack" onSubmit={submit}>
        <label className="field">
          <span>Name *</span>
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={255} placeholder="e.g. Payments squad" required />
        </label>
        <label className="field">
          <span>Description</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            maxLength={1000}
            placeholder="What does this team own?"
          />
        </label>
      </form>
    </Modal>
  );
}
