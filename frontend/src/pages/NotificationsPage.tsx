import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listNotifications, markNotificationRead } from "../api/notifications";
import { extractErrorMessage } from "../api/client";
import type { NotificationResponse } from "../types";

export function NotificationsPage() {
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  function load() {
    setLoading(true);
    listNotifications({ size: 50 })
      .then((page) => setNotifications(page.content))
      .catch((err) => setError(extractErrorMessage(err)))
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  async function handleMarkRead(id: number) {
    try {
      const updated = await markNotificationRead(id);
      setNotifications((prev) => prev.map((n) => (n.id === id ? updated : n)));
    } catch (err) {
      setError(extractErrorMessage(err));
    }
  }

  return (
    <div className="page">
      <h1>Notifications</h1>
      {error && <p className="error">{error}</p>}
      {loading && <p className="loading">Loading…</p>}

      {!loading && (
        <ul className="notification-list">
          {notifications.map((n) => (
            <li key={n.id} className={n.read ? "read" : "unread"}>
              <Link to={`/tickets/${n.ticketId}`}>{n.message}</Link>
              <span className="timestamp">{new Date(n.createdAt).toLocaleString()}</span>
              {!n.read && (
                <button onClick={() => handleMarkRead(n.id)}>Mark read</button>
              )}
            </li>
          ))}
          {notifications.length === 0 && <li>No notifications.</li>}
        </ul>
      )}
    </div>
  );
}
