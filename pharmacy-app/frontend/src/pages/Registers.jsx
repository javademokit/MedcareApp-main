import { useEffect, useState } from 'react';
import api, { errMsg } from '../api';
import { dateTime } from '../fmt';
import { Empty } from '../components/ui';

const today = () => new Date().toISOString().slice(0, 10);
const monthAgo = () => new Date(Date.now() - 30 * 86400000).toISOString().slice(0, 10);

export default function Registers() {
  const [type, setType] = useState('h1');
  const [from, setFrom] = useState(monthAgo());
  const [to, setTo] = useState(today());
  const [rows, setRows] = useState([]);
  const [err, setErr] = useState('');

  const load = () => api.get(`/registers/${type}`, { params: { from, to } }).then((r) => { setRows(r.data); setErr(''); }).catch((e) => setErr(errMsg(e)));
  useEffect(() => { load(); }, [type, from, to]);

  return (
    <>
      <div className="head no-print">
        <h1>Statutory registers</h1>
        <button className="btn ghost" onClick={() => window.print()}>Print register</button>
      </div>
      <div className="tabs no-print">
        <button className={type === 'h1' ? 'on' : ''} onClick={() => setType('h1')}>Schedule H1</button>
        <button className={type === 'x' ? 'on' : ''} onClick={() => setType('x')}>Schedule X and NDPS</button>
      </div>
      <div className="row no-print" style={{ marginBottom: 12 }}>
        From <input type="date" style={{ width: 160 }} value={from} onChange={(e) => setFrom(e.target.value)} />
        To <input type="date" style={{ width: 160 }} value={to} onChange={(e) => setTo(e.target.value)} />
      </div>
      {err && <div className="alert err">{err}</div>}
      <div className="invoice-print scroll">
        <h2>{type === 'h1' ? 'Schedule H1 register' : 'Schedule X / NDPS register'}: {from} to {to}</h2>
        <table className="tbl">
          <thead><tr><th>Date</th><th>Invoice</th><th>Medicine</th><th>Batch</th><th className="num">Qty</th><th>Patient</th><th>Doctor</th><th>Dispensed by</th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}><td>{dateTime(r.at)}</td><td>{r.invoiceNo}</td><td>{r.medicineName}</td><td>{r.batchNo}</td><td className="num">{r.qty}</td><td>{r.patientName}</td><td>{r.doctorName} {r.doctorRegNo && `(${r.doctorRegNo})`}</td><td>{r.dispensedBy} {r.pharmacistRegNo && `(${r.pharmacistRegNo})`}</td></tr>
            ))}
          </tbody>
        </table>
        {!rows.length && <Empty>No entries in this period. Entries are added automatically when these medicines are sold.</Empty>}
      </div>
    </>
  );
}
