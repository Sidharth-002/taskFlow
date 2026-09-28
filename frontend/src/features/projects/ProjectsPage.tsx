import { useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useCurrentUser } from "../auth/AuthContext";
import { useDirectory } from "../../app/DirectoryContext";
import { useShell } from "../../app/ShellContext";
import { archiveProject, createProject, updateProject, type ProjectResponse } from "./api";
import { listTickets } from "../tickets/api";
import { STATUSES, STATUS_META, isDone, isOverdue } from "../tickets/workflow";
import type { TicketListItem } from "../tickets/types";
import { can } from "../../shared/lib/permissions";
import { ALL } from "../../shared/types";
import { useAsync, useOnTicketsChanged } from "../../shared/lib/hooks";
import { useToast } from "../../shared/ui/Toast";
import { ConfirmModal, Modal } from "../../shared/ui/Modal";
import { AvatarStack } from "../../shared/ui/Avatar";
import { Icon } from "../../shared/ui/Icon";
import { EmptyState, Spinner } from "../../shared/ui/Feedback";

const COVERS = [
  "linear-gradient(135deg, #7c3aed, #ec4899)",
  "linear-gradient(135deg, #06b6d4, #6366f1)",
  "linear-gradient(135deg, #f59e0b, #ef4444)",
  "linear-gradient(135deg, #10b981, #06b6d4)",
  "linear-gradient(135deg, #8b5cf6, #22d3ee)",
  "linear-gradient(135deg, #f43f5e, #a855f7)",
];

export function ProjectsPage() {
  const me = useCurrentUser();
  const { projects, ready, reloadProjects } = useDirectory();
  const { openCreateTicket } = useShell();
  const toast = useToast();
  const isAdmin = can.manageOrganization(me.role);
  const [editing, setEditing] = useState<ProjectResponse | "new" | null>(null);
  const [archiving, setArchiving] = useState<ProjectResponse | null>(null);
  const [showArchived, setShowArchived] = useState(false);

  const tickets = useAsync(() => listTickets({ size: ALL }).then((p) => p.content), []);
  useOnTicketsChanged(tickets.reload);

  const byProject = useMemo(() => {
    const map = new Map<number, TicketListItem[]>();
    (tickets.data ?? []).forEach((t) => map.set(t.projectId, [...(map.get(t.projectId) ?? []), t]));
    return map;
  }, [tickets.data]);

  const shown = projects.filter((p) => showArchived || p.status === "ACTIVE");
  const archivedCount = projects.filter((p) => p.status === "ARCHIVED").length;

  async function archive() {
    if (!archiving) return;
    try {
      await archiveProject(archiving.id);
      await reloadProjects();
      toast.success(`${archiving.name} archived`);
    } catch (err) {
      toast.error(err);
    } finally {
      setArchiving(null);
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="eyebrow">
            <Icon name="folder" size={14} /> {projects.length - archivedCount} active projects
          </p>
          <h1 className="gradient-text">Projects</h1>
          <p className="muted">Every issue belongs to a project. Click one to open its board.</p>
        </div>
        <div className="row">
          {archivedCount > 0 && (
            <button className={`chip ${showArchived ? "active" : ""}`} onClick={() => setShowArchived((s) => !s)}>
              <Icon name="archive" size={14} /> Show archived ({archivedCount})
            </button>
          )}
          {isAdmin && (
            <button className="btn btn-primary" onClick={() => setEditing("new")}>
              <Icon name="plus" /> New project
            </button>
          )}
        </div>
      </header>

      {!ready ? (
        <Spinner />
      ) : shown.length === 0 ? (
        <EmptyState
          icon="folder"
          title="No projects yet"
          action={
            isAdmin && (
              <button className="btn btn-primary" onClick={() => setEditing("new")}>
                <Icon name="plus" /> Create a project
              </button>
            )
          }
        >
          {isAdmin ? "Projects are the top-level home for issues." : "Ask an admin to create one."}
        </EmptyState>
      ) : (
        <div className="project-grid">
          {shown.map((project, i) => {
            const items = byProject.get(project.id) ?? [];
            const open = items.filter((t) => !isDone(t.status)).length;
            const done = items.length - open;
            const pct = items.length ? Math.round((done / items.length) * 100) : 0;
            const people = [...new Set(items.map((t) => t.assignedToName).filter((n): n is string => !!n))];
            return (
              <article key={project.id} className={`project-card ${project.status === "ARCHIVED" ? "archived" : ""}`}>
                <div className="project-cover" style={{ background: COVERS[i % COVERS.length] }}>
                  <span className="project-initial">{project.name[0]?.toUpperCase()}</span>
                  {project.status === "ARCHIVED" && <span className="pill">Archived</span>}
                </div>
                <div className="project-body">
                  <Link to={`/board?project=${project.id}`} className="project-name">
                    {project.name}
                  </Link>
                  <p className="muted clamp-2">{project.description || "No description."}</p>

                  <div className="progress" title={`${pct}% resolved or closed`}>
                    {STATUSES.map((s) => {
                      const n = items.filter((t) => t.status === s).length;
                      return n ? <span key={s} style={{ flex: n, background: STATUS_META[s].color }} /> : null;
                    })}
                  </div>
                  <div className="project-meta">
                    <span>
                      <strong>{open}</strong> open
                    </span>
                    <span>
                      <strong>{pct}%</strong> done
                    </span>
                    {items.some(isOverdue) && (
                      <span className="hot">
                        <Icon name="flame" size={12} /> {items.filter(isOverdue).length} overdue
                      </span>
                    )}
                    <span className="spacer" />
                    <AvatarStack names={people} max={3} />
                  </div>
                </div>
                <footer className="project-actions">
                  <Link to={`/board?project=${project.id}`} className="btn btn-ghost btn-sm">
                    <Icon name="board" size={14} /> Board
                  </Link>
                  {project.status === "ACTIVE" && (
                    <button className="btn btn-ghost btn-sm" onClick={() => openCreateTicket(project.id)}>
                      <Icon name="plus" size={14} /> Issue
                    </button>
                  )}
                  <span className="spacer" />
                  {isAdmin && project.status === "ACTIVE" && (
                    <>
                      <button className="icon-btn icon-btn-sm" aria-label="Edit project" onClick={() => setEditing(project)}>
                        <Icon name="edit" size={14} />
                      </button>
                      <button className="icon-btn icon-btn-sm" aria-label="Archive project" onClick={() => setArchiving(project)}>
                        <Icon name="archive" size={14} />
                      </button>
                    </>
                  )}
                </footer>
              </article>
            );
          })}
        </div>
      )}

      {editing && (
        <ProjectFormModal
          project={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={async () => {
            await reloadProjects();
            setEditing(null);
          }}
        />
      )}
      {archiving && (
        <ConfirmModal
          title={`Archive ${archiving.name}?`}
          message="Existing issues stay put, but no new issues can be created in an archived project."
          confirmLabel="Archive"
          onConfirm={archive}
          onClose={() => setArchiving(null)}
        />
      )}
    </div>
  );
}

function ProjectFormModal({
  project,
  onClose,
  onSaved,
}: {
  project: ProjectResponse | null;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const toast = useToast();
  const [name, setName] = useState(project?.name ?? "");
  const [description, setDescription] = useState(project?.description ?? "");
  const [saving, setSaving] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      const input = { name: name.trim(), description: description.trim() || undefined };
      const saved = project ? await updateProject(project.id, input) : await createProject(input);
      toast.success(project ? "Project updated" : `${saved.name} created`);
      await onSaved();
    } catch (err) {
      toast.error(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title={project ? `Edit ${project.name}` : "New project"}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" type="submit" form="project-form" disabled={saving || !name.trim()}>
            {saving ? "Saving…" : project ? "Save" : "Create project"}
          </button>
        </>
      }
    >
      <form id="project-form" className="stack" onSubmit={submit}>
        <label className="field">
          <span>Name *</span>
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={255} placeholder="e.g. Mobile app" required />
        </label>
        <label className="field">
          <span>Description</span>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} maxLength={1000} />
        </label>
      </form>
    </Modal>
  );
}
