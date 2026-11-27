import { daysUntil, date } from '../fmt';

export function ExpiryChip({ value }) {
  if (!value) return null;
  const d = daysUntil(value);
  const cls = d <= 30 ? 'bad' : d <= 90 ? 'soon' : 'ok';
  const label = d < 0 ? `Expired ${date(value)}` : date(value);
  return <span className={`exp ${cls}`} title={d < 0 ? 'Expired' : `${d} days left`}>{label}</span>;
}

export const ScheduleBadge = ({ value }) => <span className={`badge ${value}`}>{value === 'OTC' ? 'OTC' : `Sch. ${value}`}</span>;

export function Modal({ title, onClose, wide, children }) {
  return (
    <div className="overlay no-print" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="head">
          <h2 style={{ margin: 0 }}>{title}</h2>
          <button className="btn ghost sm" onClick={onClose}>Close</button>
        </div>
        {children}
      </div>
    </div>
  );
}

export const Field = ({ label, children, full }) => (
  <label className={`f ${full ? 'full' : ''}`}>
    {label}
    {children}
  </label>
);

export const Empty = ({ children }) => <div className="empty">{children}</div>;
