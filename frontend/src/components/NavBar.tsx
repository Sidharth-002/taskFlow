import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function NavBar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) {
    return null;
  }

  async function handleLogout() {
    await logout();
    navigate("/login");
  }

  return (
    <header className="nav-bar">
      <Link to="/tickets" className="nav-brand">
        FlowDesk
      </Link>
      <nav>
        <Link to="/tickets">Tickets</Link>
        <Link to="/notifications">Notifications</Link>
      </nav>
      <div className="nav-user">
        <span>
          {user.firstName} {user.lastName} · {user.role}
        </span>
        <button onClick={handleLogout}>Log out</button>
      </div>
    </header>
  );
}
