import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../features/auth/AuthContext";
import { ProtectedRoute } from "../features/auth/ProtectedRoute";
import { LoginPage } from "../features/auth/LoginPage";
import { RegisterPage } from "../features/auth/RegisterPage";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { BoardPage } from "../features/tickets/BoardPage";
import { TicketsPage } from "../features/tickets/TicketsPage";
import { TicketDetailPage } from "../features/tickets/TicketDetailPage";
import { ProjectsPage } from "../features/projects/ProjectsPage";
import { TeamsPage } from "../features/teams/TeamsPage";
import { PeoplePage } from "../features/people/PeoplePage";
import { NotificationsPage } from "../features/notifications/NotificationsPage";
import { ToastProvider } from "../shared/ui/Toast";
import { DirectoryProvider } from "./DirectoryContext";
import { AppShell } from "./AppShell";

export function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            element={
              <ProtectedRoute>
                <DirectoryProvider>
                  <AppShell />
                </DirectoryProvider>
              </ProtectedRoute>
            }
          >
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/board" element={<BoardPage />} />
            <Route path="/tickets" element={<TicketsPage />} />
            <Route path="/tickets/new" element={<Navigate to="/board" replace />} />
            <Route path="/tickets/:id" element={<TicketDetailPage />} />
            <Route path="/projects" element={<ProjectsPage />} />
            <Route path="/teams" element={<TeamsPage />} />
            <Route path="/people" element={<PeoplePage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </ToastProvider>
  );
}
