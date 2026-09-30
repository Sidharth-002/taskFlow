import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { getTicket, updateTicket } from "../api/tickets";
import { addComment, listComments } from "../api/comments";
import { extractErrorMessage } from "../api/client";
import type { CommentResponse, TicketDetail, TicketStatus } from "../types";

const STATUSES: TicketStatus[] = ["OPEN", "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED"];

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const ticketId = Number(id);

  const [ticket, setTicket] = useState<TicketDetail | null>(null);
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusDraft, setStatusDraft] = useState<TicketStatus | "">("");
  const [savingStatus, setSavingStatus] = useState(false);
  const [newComment, setNewComment] = useState("");
  const [postingComment, setPostingComment] = useState(false);

  function load() {
    setLoading(true);
    setError(null);
    Promise.all([getTicket(ticketId), listComments(ticketId)])
      .then(([t, c]) => {
        setTicket(t);
        setStatusDraft(t.status);
        setComments(c);
      })
      .catch((err) => setError(extractErrorMessage(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, [ticketId]);

  async function handleStatusSave() {
    if (!ticket || statusDraft === ticket.status) {
      return;
    }
    setSavingStatus(true);
    setError(null);
    try {
      const updated = await updateTicket(ticket.id, { status: statusDraft as TicketStatus });
      setTicket(updated);
    } catch (err) {
      setError(extractErrorMessage(err));
      setStatusDraft(ticket.status);
    } finally {
      setSavingStatus(false);
    }
  }

  async function handleAddComment(event: FormEvent) {
    event.preventDefault();
    if (!newComment.trim()) {
      return;
    }
    setPostingComment(true);
    setError(null);
    try {
      const comment = await addComment(ticketId, newComment.trim());
      setComments((prev) => [...prev, comment]);
      setNewComment("");
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setPostingComment(false);
    }
  }

  if (loading) {
    return <p className="loading">Loading…</p>;
  }
  if (error && !ticket) {
    return <p className="error">{error}</p>;
  }
  if (!ticket) {
    return null;
  }

  return (
    <div className="page">
      <h1>{ticket.title}</h1>
      {error && <p className="error">{error}</p>}

      <dl className="ticket-fields">
        <dt>Priority</dt>
        <dd>{ticket.priority}</dd>
        <dt>Due date</dt>
        <dd>{ticket.dueDate ? new Date(ticket.dueDate).toLocaleDateString() : "—"}</dd>
        <dt>Description</dt>
        <dd>{ticket.description || "—"}</dd>
      </dl>

      <div className="status-editor">
        <label>
          Status
          <select value={statusDraft} onChange={(e) => setStatusDraft(e.target.value as TicketStatus)}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <button onClick={handleStatusSave} disabled={savingStatus || statusDraft === ticket.status}>
          {savingStatus ? "Saving…" : "Save status"}
        </button>
        <small>
          Not every transition is legal (e.g. OPEN can't jump straight to CLOSED) - the server validates and this
          just shows whatever error it returns.
        </small>
      </div>

      <section className="comments">
        <h2>Comments</h2>
        {comments.length === 0 && <p>No comments yet.</p>}
        <ul>
          {comments.map((c) => (
            <li key={c.id}>
              <strong>{c.authorName}</strong> <span className="timestamp">{new Date(c.createdAt).toLocaleString()}</span>
              <p>{c.body}</p>
            </li>
          ))}
        </ul>
        <form onSubmit={handleAddComment} className="comment-form">
          <textarea
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            placeholder="Add a comment…"
            rows={3}
          />
          <button type="submit" disabled={postingComment}>
            {postingComment ? "Posting…" : "Post comment"}
          </button>
        </form>
      </section>
    </div>
  );
}
