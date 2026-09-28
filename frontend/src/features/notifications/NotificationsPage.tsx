import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { listNotifications, markNotificationRead, type NotificationResponse, type NotificationType } from "./api";
import { useShell } from "../../app/ShellContext";
import { useAsync } from "../../shared/lib/hooks";
import { ticketKey, timeAgo } from "../../shared/lib/format";
import { useToast } from "../../shared/ui/Toast";
import { Icon, type IconName } from "../../shared/ui/Icon";
import { EmptyState, ErrorBanner, Spinner } from "../../shared/ui/Feedback";

const TYPE_META: Record<NotificationType, { icon: IconName; label: string; tone: string }> = {
  TICKET_ASSIGNED: { icon: "user", label: "Assigned", tone: "violet" },
  TICKET_STATUS_CHANGED: { icon: "arrowRight", label: "Status", tone: "cyan" },
  TICKET_CLOSED: { icon: "check", label: "Closed", tone: "green" },
  TICKET_COMMENT_ADDED: { icon: "message", label: "Comment", tone: "amber" },
  TICKET_OVERDUE: { icon: "flame", label: "Overdue", tone: "red" },
};

function dayLabel(iso: string): string {
  const date = new Date(iso);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === today.toDateString()) return "Today";
  if (date.toDateString() === yesterday.toDateString()) return "Yesterday";
  return date.toLocaleDateString(undefined, { weekday: "long", month: "short", day: "numeric" });
}

export function NotificationsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { refreshUnread } = useShell();
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const { data, setData, error, loading, reload } = useAsync(() => listNotifications({ page, size: 50 }), [page]);

  const items = (data?.content ?? []).filter((n) => !unreadOnly || !n.read);
  const unread = (data?.content ?? []).filter((n) => !n.read);

  function markLocal(ids: number[]) {
    setData((prev) => (prev ? { ...prev, content: prev.content.map((n) => (ids.includes(n.id) ? { ...n, read: true } : n)) } : prev));
  }

  async function open(n: NotificationResponse) {
    if (!n.read) {
      markLocal([n.id]);
      markNotificationRead(n.id).then(refreshUnread).catch(() => undefined);
    }
    navigate(`/tickets/${n.ticketId}`);
  }

  async function markAll() {
    const ids = unread.map((n) => n.id);
    markLocal(ids);
    const results = await Promise.allSettled(ids.map(markNotificationRead));
    refreshUnread();
    const failed = results.filter((r) => r.status === "rejected").length;
    if (failed) toast.error(`${failed} couldn't be marked read`);
    else toast.success("Inbox zero ✨");
  }

  const groups: { label: string; items: NotificationResponse[] }[] = [];
  items.forEach((n) => {
    const label = dayLabel(n.createdAt);
    const last = groups[groups.length - 1];
    if (last?.label === label) last.items.push(n);
    else groups.push({ label, items: [n] });
  });

  return (
    <div className="page page-narrow">
      <header className="page-header">
        <div>
          <p className="eyebrow">
            <Icon name="bell" size={14} /> {unread.length} unread
          </p>
          <h1 className="gradient-text">Inbox</h1>
          <p className="muted">Updates on issues you're involved in, delivered by the Kafka consumers.</p>
        </div>
        <div className="row">
          <button className={`chip ${unreadOnly ? "active" : ""}`} onClick={() => setUnreadOnly((u) => !u)}>
            Unread only
          </button>
          <button className="btn btn-ghost" onClick={markAll} disabled={unread.length === 0}>
            <Icon name="check" /> Mark all read
          </button>
        </div>
      </header>

      {error && <ErrorBanner message={error} onRetry={reload} />}

      {loading && !data ? (
        <Spinner />
      ) : items.length === 0 ? (
        <EmptyState icon="inbox" title={unreadOnly ? "All caught up" : "Your inbox is empty"}>
          You'll hear about assignments, status changes, comments and overdue issues here.
        </EmptyState>
      ) : (
        groups.map((g) => (
          <section key={g.label} className="inbox-group">
            <h2 className="inbox-day">{g.label}</h2>
            <ul className="inbox-list">
              {g.items.map((n) => {
                const meta = TYPE_META[n.type] ?? TYPE_META.TICKET_STATUS_CHANGED;
                return (
                  <li key={n.id}>
                    <button className={`inbox-item ${n.read ? "" : "unread"}`} onClick={() => open(n)}>
                      <span className={`inbox-icon tone-${meta.tone}`}>
                        <Icon name={meta.icon} size={16} />
                      </span>
                      <span className="inbox-text">
                        <span>{n.message}</span>
                        <small className="muted">
                          {meta.label} · <span className="mono">{ticketKey(n.ticketId)}</span> · {timeAgo(n.createdAt)}
                        </small>
                      </span>
                      {!n.read && <span className="unread-dot" aria-label="Unread" />}
                    </button>
                  </li>
                );
              })}
            </ul>
          </section>
        ))
      )}

      {data && data.totalPages > 1 && (
        <div className="pagination">
          <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            <Icon name="chevronLeft" /> Newer
          </button>
          <span>
            Page {data.number + 1} of {data.totalPages}
          </span>
          <button className="btn btn-ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
            Older <Icon name="chevronRight" />
          </button>
        </div>
      )}
    </div>
  );
}
