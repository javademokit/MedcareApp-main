import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth';

export default function Layout() {
  const { user, logout, hasRole } = useAuth();
  const link = (to, label, show = true) =>
    show && (
      <NavLink to={to} end={to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>
        {label}
      </NavLink>
    );
  return (
    <div className="shell">
      <nav className="side no-print">
        <div className="brand">
          Pharmacy Desk
          <small>Dispensing and stock</small>
        </div>
        {link('/', 'Dashboard')}
        {link('/pos', 'New bill', hasRole('ADMIN', 'MANAGER', 'PHARMACIST', 'CASHIER'))}
        {link('/sales', 'Invoices')}
        {link('/medicines', 'Medicines')}
        {link('/inventory', 'Stock and expiry')}
        {link('/purchases', 'Purchases', hasRole('ADMIN', 'MANAGER', 'STOREKEEPER', 'AUDITOR'))}
        {link('/registers', 'Registers', hasRole('ADMIN', 'MANAGER', 'PHARMACIST', 'AUDITOR'))}
        <div className="who">
          {user.fullName}
          <br />
          {user.roles.join(', ')}
          <br />
          <button className="btn ghost sm" onClick={logout}>Sign out</button>
        </div>
      </nav>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
