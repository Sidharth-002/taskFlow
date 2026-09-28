import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Modal } from "../../shared/ui/Modal";
import { useToast } from "../../shared/ui/Toast";
import { useDirectory } from "../../app/DirectoryContext";
import { useCurrentUser } from "../auth/AuthContext";
import { can } from "../../shared/lib/permissions";
import { fromDateInput, fullName, ticketKey } from "../../shared/lib/format";
import { announceTicketsChanged } from "../../shared/lib/hooks";
import { confetti } from "../../shared/ui/confetti";
import { createProject } from "../projects/api";
import { createTicket } from "./api";
import { PRIORITIES, PRIORITY_META } from "./workflow";
import { PriorityIcon } from "./TicketBadges";
import type { TicketPriority } from "./types";

export function CreateTicketModal({
  onClose,
  defaultProjectId,
}: {
  onClose: () => void;
  defaultProjectId?: number;
}) {
  const me = useCurrentUser();
  const toast = useToast();
  const navigate = useNavigate();
  const { projects, teams, users, reloadProjects } = useDirectory();
  const activeProjects = projects.filter((p) => p.status === "ACTIVE");
  const canAssign = can.assignTicket(me.role);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<TicketPriority>("MEDIUM");
  const [projectId, setProjectId] = useState<number | "">(defaultProjectId ?? activeProjects[0]?.id ?? "");
  const [teamId, setTeamId] = useState<number | "">("");
  const [assignedToId, setAssignedToId] = useState<number | "">("");
  const [dueDate, setDueDate] = useState("");
  const [newProjectName, setNewProjectName] = useState("");
  const [createAnother, setCreateAnother] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleCreateProject() {
    if (!newProjectName.trim()) return;
    try {
      const project = await createProject({ name: newProjectName.trim() });
      await reloadProjects();
      setProjectId(project.id);
      setNewProjectName("");
      toast.success(`Project "${project.name}" created`);
    } catch (err) {
      toast.error(err);
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title.trim() || projectId === "") return;
    setSubmitting(true);
    try {
      const ticket = await createTicket({
        title: title.trim(),
        description: description.trim() || undefined,
        priority,
        projectId,
        teamId: teamId === "" ? undefined : teamId,
        assignedToId: assignedToId === "" ? undefined : assignedToId,
        dueDate: fromDateInput(dueDate),
      });
      announceTicketsChanged();
      toast.success(`${ticketKey(ticket.id)} created`);
      if (priority === "CRITICAL") confetti();
      if (createAnother) {
        setTitle("");
        setDescription("");
      } else {
        onClose();
        navigate(`/tickets/${ticket.id}`);
      }
    } catch (err) {
      toast.error(err);
    } finally {
      setSubmitting(false);
    }
  }

  const noProjects = activeProjects.length === 0;

  return (
    <Modal
      title="Create issue"
      subtitle="Describe the work - it lands in To do on the board."
      onClose={onClose}
      wide
      footer={
        <>
          <label className="check-inline">
            <input type="checkbox" checked={createAnother} onChange={(e) => setCreateAnother(e.target.checked)} />
            Create another
          </label>
          <span className="spacer" />
          <button className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            type="submit"
            form="create-ticket-form"
            disabled={submitting || !title.trim() || projectId === ""}
          >
            {submitting ? "Creating…" : "Create"}
          </button>
        </>
      }
    >
      <form id="create-ticket-form" className="form-grid" onSubmit={handleSubmit}>
        <label className="field span-2">
          <span>Summary *</span>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Checkout button spins forever on Safari"
            maxLength={255}
            required
          />
        </label>

        <label className="field span-2">
          <span>Description</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={5}
            placeholder="Steps to reproduce, expected vs actual, links…"
          />
        </label>

        <label className="field">
          <span>Project *</span>
          {noProjects ? (
            <em className="muted">No active projects yet</em>
          ) : (
            <select value={projectId} onChange={(e) => setProjectId(Number(e.target.value))} required>
              {activeProjects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          )}
        </label>

        <div className="field">
          <span>Priority</span>
          <div className="segmented">
            {PRIORITIES.map((p) => (
              <button
                type="button"
                key={p}
                className={priority === p ? "active" : ""}
                onClick={() => setPriority(p)}
                title={PRIORITY_META[p].label}
              >
                <PriorityIcon priority={p} />
                {PRIORITY_META[p].label}
              </button>
            ))}
          </div>
        </div>

        {noProjects && (
          <div className="field span-2 inline-create">
            {can.manageOrganization(me.role) ? (
              <>
                <input
                  value={newProjectName}
                  onChange={(e) => setNewProjectName(e.target.value)}
                  placeholder="New project name"
                />
                <button type="button" className="btn btn-secondary" onClick={handleCreateProject}>
                  Create project
                </button>
              </>
            ) : (
              <p className="muted">Every issue needs a project - ask an admin to create one.</p>
            )}
          </div>
        )}

        {canAssign && (
          <>
            <label className="field">
              <span>Team</span>
              <select value={teamId} onChange={(e) => setTeamId(e.target.value ? Number(e.target.value) : "")}>
                <option value="">No team</option>
                {teams
                  .filter((t) => t.active)
                  .map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
              </select>
            </label>
            <label className="field">
              <span>Assignee</span>
              <select
                value={assignedToId}
                onChange={(e) => setAssignedToId(e.target.value ? Number(e.target.value) : "")}
              >
                <option value="">Unassigned</option>
                {users
                  .filter((u) => u.active)
                  .map((u) => (
                    <option key={u.id} value={u.id}>
                      {fullName(u)}
                      {u.id === me.id ? " (you)" : ""}
                    </option>
                  ))}
              </select>
            </label>
          </>
        )}

        <label className="field">
          <span>Due date</span>
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        </label>
      </form>
    </Modal>
  );
}
