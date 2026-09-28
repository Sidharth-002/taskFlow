import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { createProject, listProjects } from "../api/projects";
import { createTicket } from "../api/tickets";
import { extractErrorMessage } from "../api/client";
import type { ProjectResponse, TicketPriority } from "../types";

const PRIORITIES: TicketPriority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];

export function NewTicketPage() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<ProjectResponse[]>([]);
  const [projectId, setProjectId] = useState<number | "">("");
  const [newProjectName, setNewProjectName] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<TicketPriority>("MEDIUM");
  const [dueDate, setDueDate] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [loadingProjects, setLoadingProjects] = useState(true);

  useEffect(() => {
    listProjects()
      .then((page) => {
        setProjects(page.content);
        if (page.content.length > 0) {
          setProjectId(page.content[0].id);
        }
      })
      .catch((err) => setError(extractErrorMessage(err)))
      .finally(() => setLoadingProjects(false));
  }, []);

  async function handleCreateProject() {
    if (!newProjectName.trim()) {
      return;
    }
    setError(null);
    try {
      const project = await createProject({ name: newProjectName.trim() });
      setProjects((prev) => [...prev, project]);
      setProjectId(project.id);
      setNewProjectName("");
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!projectId) {
      setError("Choose or create a project first.");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const ticket = await createTicket({
        title,
        description: description || undefined,
        priority,
        projectId,
        dueDate: dueDate ? new Date(dueDate).toISOString() : undefined,
      });
      navigate(`/tickets/${ticket.id}`);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <h1>New ticket</h1>
      {error && <p className="error">{error}</p>}

      {!loadingProjects && projects.length === 0 && (
        <div className="inline-create-project">
          <p>
            This organization has no projects yet. Every ticket belongs to one, so create one first (only
            <code> ORG_ADMIN</code> can - see the note in the README on why this is the only project-management
            surface exposed here).
          </p>
          <input
            placeholder="Project name"
            value={newProjectName}
            onChange={(e) => setNewProjectName(e.target.value)}
          />
          <button type="button" onClick={handleCreateProject}>
            Create project
          </button>
        </div>
      )}

      <form onSubmit={handleSubmit} className="ticket-form">
        <label>
          Project
          <select
            value={projectId}
            onChange={(e) => setProjectId(Number(e.target.value))}
            required
            disabled={projects.length === 0}
          >
            <option value="" disabled>
              Select a project
            </option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
        </label>
        <label>
          Description
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={4} />
        </label>
        <label>
          Priority
          <select value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority)}>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </label>
        <label>
          Due date
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        </label>
        <button type="submit" disabled={submitting || projects.length === 0}>
          {submitting ? "Creating…" : "Create ticket"}
        </button>
      </form>
    </div>
  );
}
