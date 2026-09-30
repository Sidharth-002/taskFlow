import { useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useCurrentUser } from "../auth/AuthContext";
import { useDirectory } from "../../app/DirectoryContext";
import { changeUserRole, createUser, setUserActive } from "./api";
import { listTickets } from "../tickets/api";
import { isDone } from "../tickets/workflow";
import { can, ROLE_LABEL, ROLE_SCOPE } from "../../shared/lib/permissions";
import { ALL, type Role, type UserSummary } from "../../shared/types";
import { fullName } from "../../shared/lib/format";
import { useAsync, useOnTicketsChanged } from "../../shared/lib/hooks";
import { useToast } from "../../shared/ui/Toast";
import { Modal } from "../../shared/ui/Modal";
import { Avatar } from "../../shared/ui/Avatar";
import { RolePill } from "../../shared/ui/RolePill";
import { Icon } from "../../shared/ui/Icon";
import { EmptyState, Spinner } from "../../shared/ui/Feedback";

const ASSIGNABLE_ROLES: Role[] = ["ORG_ADMIN", "TEAM_LEAD", "AGENT", "USER"];

export function PeoplePage() {
  const me = useCurrentUser();
  const toast = useToast();
  const { users, ready, reloadUsers } = useDirectory();
  const isAdmin = can.manageOrganization(me.role);
  const [query, setQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState<Role | "">("");
  const [inviting, setInviting] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);

  const tickets = useAsync(() => listTickets({ size: ALL }).then((p) => p.content), []);
  useOnTicketsChanged(tickets.reload);

  const openByUser = useMemo(() => {
    const counts = new Map<number, number>();
    (tickets.data ?? []).forEach((t) => {
      if (t.assignedToId !== null && !isDone(t.status)) counts.set(t.assignedToId, (counts.get(t.assignedToId) ?? 0) + 1);
    });
    return counts;
  }, [tickets.data]);
  const maxLoad = Math.max(1, ...openByUser.values());

  const q = query.trim().toLowerCase();
  const shown = users.filter(
    (u) =>
      (!q || fullName(u).toLowerCase().includes(q) || u.email.toLowerCase().includes(q)) &&
      (!roleFilter || u.role === roleFilter),
  );
  const roleCounts = ASSIGNABLE_ROLES.map((r) => ({ role: r, n: users.filter((u) => u.role === r).length }));

  async function change(user: UserSummary, action: () => Promise<UserSummary>, message: string) {
    setBusyId(user.id);
    try {
      await action();
      await reloadUsers();
      toast.success(message);
    } catch (err) {
      toast.error(err);
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">
            <Icon name="people" size={14} /> {users.filter((u) => u.active).length} active people
          </p>
          <h1 className="gradient-text">People</h1>
          <p className="muted">Everyone in your organization, their role, and what's on their plate.</p>
        </div>
        {isAdmin && (
          <button className="btn btn-primary" onClick={() => setInviting(true)}>
            <Icon name="plus" /> Add person
          </button>
        )}
      </header>

      <div className="role-cards">
        {roleCounts.map(({ role, n }) => (
          <button
            key={role}
            className={`role-card role-${role.toLowerCase()} ${roleFilter === role ? "active" : ""}`}
            onClick={() => setRoleFilter((r) => (r === role ? "" : role))}
          >
            <strong>{n}</strong>
            <span>{ROLE_LABEL[role]}s</span>
            <small>{ROLE_SCOPE[role]}</small>
          </button>
        ))}
      </div>

      <div className="filter-bar">
        <label className="search-field">
          <Icon name="search" size={16} />
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search by name or email…" />
        </label>
        {roleFilter && (
          <button className="btn btn-ghost btn-sm" onClick={() => setRoleFilter("")}>
            Clear role filter
          </button>
        )}
      </div>

      <div className="panel table-panel">
        {!ready ? (
          <Spinner />
        ) : shown.length === 0 ? (
          <EmptyState icon="people" title="Nobody matches" />
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Person</th>
                <th>Role</th>
                <th>Open issues</th>
                <th>Status</th>
                {isAdmin && <th aria-label="Actions" />}
              </tr>
            </thead>
            <tbody>
              {shown.map((u) => {
                const load = openByUser.get(u.id) ?? 0;
                const self = u.id === me.id;
                return (
                  <tr key={u.id} className={u.active ? "" : "row-inactive"}>
                    <td>
                      <span className="cell-person">
                        <Avatar name={fullName(u)} size={34} />
                        <span className="stack-tight">
                          <strong>
                            {fullName(u)} {self && <span className="pill">You</span>}
                          </strong>
                          <small className="muted">{u.email}</small>
                        </span>
                      </span>
                    </td>
                    <td>
                      {isAdmin && !self && u.role !== "SUPER_ADMIN" ? (
                        <select
                          value={u.role}
                          disabled={busyId === u.id}
                          onChange={(e) => {
                            const role = e.target.value as Role;
                            void change(u, () => changeUserRole(u.id, role), `${fullName(u)} is now ${ROLE_LABEL[role]}`);
                          }}
                        >
                          {ASSIGNABLE_ROLES.map((r) => (
                            <option key={r} value={r}>
                              {ROLE_LABEL[r]}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <RolePill role={u.role} />
                      )}
                    </td>
                    <td>
                      <Link to={`/board?assignee=${u.id}`} className="load-meter" title="Open this person's board">
                        <span className="load-track">
                          <span
                            className={`load-fill ${load >= 8 ? "heavy" : ""}`}
                            style={{ width: `${(load / maxLoad) * 100}%` }}
                          />
                        </span>
                        <strong>{load}</strong>
                      </Link>
                    </td>
                    <td>
                      <span className={`status-dot-label ${u.active ? "on" : "off"}`}>
                        {u.active ? "Active" : "Deactivated"}
                      </span>
                    </td>
                    {isAdmin && (
                      <td className="cell-actions">
                        {!self && (
                          <button
                            className={`btn btn-sm ${u.active ? "btn-danger-ghost" : "btn-ghost"}`}
                            disabled={busyId === u.id}
                            onClick={() =>
                              change(
                                u,
                                () => setUserActive(u.id, !u.active),
                                u.active ? `${fullName(u)} deactivated - their sessions were revoked` : `${fullName(u)} reactivated`,
                              )
                            }
                          >
                            {u.active ? "Deactivate" : "Reactivate"}
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {inviting && (
        <AddPersonModal
          onClose={() => setInviting(false)}
          onCreated={async (user) => {
            await reloadUsers();
            setInviting(false);
            toast.success(`${fullName(user)} added as ${ROLE_LABEL[user.role]}`);
          }}
        />
      )}
    </div>
  );
}

function AddPersonModal({ onClose, onCreated }: { onClose: () => void; onCreated: (u: UserSummary) => Promise<void> }) {
  const toast = useToast();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("AGENT");
  const [saving, setSaving] = useState(false);
  const passwordOk = /^(?=.*[A-Za-z])(?=.*\d).{8,100}$/.test(password);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      const user = await createUser({ firstName: firstName.trim(), lastName: lastName.trim(), email: email.trim(), password, role });
      await onCreated(user);
    } catch (err) {
      toast.error(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title="Add a person"
      subtitle="They can log in right away with this email and password."
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" type="submit" form="person-form" disabled={saving || !passwordOk}>
            {saving ? "Adding…" : "Add person"}
          </button>
        </>
      }
    >
      <form id="person-form" className="form-grid" onSubmit={submit}>
        <label className="field">
          <span>First name *</span>
          <input value={firstName} onChange={(e) => setFirstName(e.target.value)} maxLength={100} required />
        </label>
        <label className="field">
          <span>Last name *</span>
          <input value={lastName} onChange={(e) => setLastName(e.target.value)} maxLength={100} required />
        </label>
        <label className="field span-2">
          <span>Email *</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={255} required />
        </label>
        <label className="field span-2">
          <span>Temporary password *</span>
          <input type="text" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="off" required />
          <small className={password && !passwordOk ? "text-danger" : "muted"}>
            At least 8 characters, with both letters and numbers.
          </small>
        </label>
        <div className="field span-2">
          <span>Role</span>
          <div className="role-picker">
            {ASSIGNABLE_ROLES.map((r) => (
              <button type="button" key={r} className={`role-option ${role === r ? "active" : ""}`} onClick={() => setRole(r)}>
                <strong>{ROLE_LABEL[r]}</strong>
                <small>{ROLE_SCOPE[r]}</small>
              </button>
            ))}
          </div>
        </div>
      </form>
    </Modal>
  );
}
