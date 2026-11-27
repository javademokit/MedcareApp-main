import { useEffect, useState } from 'react';
import api, { errMsg } from '../api';
import { useAuth } from '../auth';
import { Empty, Field, Modal, ScheduleBadge } from '../components/ui';

const blank = { brandName: '', genericName: '', manufacturer: '', dosageForm: 'Tablet', unitsPerPack: 10, hsnCode: '', gstPercent: 12, schedule: 'OTC', storage: 'ROOM', reorderLevel: 0, barcode: '' };

export default function Medicines() {
  const { hasRole } = useAuth();
  const canEdit = hasRole('ADMIN', 'MANAGER', 'PHARMACIST');
  const [rows, setRows] = useState([]);
  const [q, setQ] = useState('');
  const [form, setForm] = useState(null);
  const [err, setErr] = useState('');

  const load = () => api.get('/medicines', { params: { q } }).then((r) => setRows(r.data));
  useEffect(() => { const t = setTimeout(load, 250); return () => clearTimeout(t); }, [q]);

  const save = async (e) => {
    e.preventDefault(); setErr('');
    const body = { ...form, unitsPerPack: +form.unitsPerPack, gstPercent: +form.gstPercent, reorderLevel: +form.reorderLevel };
    try {
      form.id ? await api.put(`/medicines/${form.id}`, body) : await api.post('/medicines', body);
      setForm(null); load();
    } catch (x) { setErr(errMsg(x)); }
  };
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  return (
    <>
      <div className="head">
        <h1>Medicines</h1>
        <div className="row">
          <input style={{ width: 260 }} placeholder="Search brand, salt or barcode" value={q} onChange={(e) => setQ(e.target.value)} />
          {canEdit && <button className="btn" onClick={() => { setErr(''); setForm(blank); }}>Add medicine</button>}
        </div>
      </div>
      <div className="scroll">
        <table className="tbl">
          <thead><tr><th>Brand</th><th>Salt / generic</th><th>Schedule</th><th className="num">Units/pack</th><th className="num">GST %</th><th>HSN</th><th className="num">Reorder at</th><th /></tr></thead>
          <tbody>
            {rows.map((m) => (
              <tr key={m.id}>
                <td><b>{m.brandName}</b><br /><small style={{ color: 'var(--muted)' }}>{m.manufacturer}</small></td>
                <td>{m.genericName}</td><td><ScheduleBadge value={m.schedule} /></td>
                <td className="num">{m.unitsPerPack}</td><td className="num">{Number(m.gstPercent)}</td><td>{m.hsnCode}</td><td className="num">{m.reorderLevel}</td>
                <td>{canEdit && <button className="link" onClick={() => { setErr(''); setForm({ ...m }); }}>Edit</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!rows.length && <Empty>No medicines match. Add one to start tracking stock.</Empty>}
      </div>

      {form && (
        <Modal title={form.id ? 'Edit medicine' : 'Add medicine'} onClose={() => setForm(null)}>
          {err && <div className="alert err">{err}</div>}
          <form className="form" onSubmit={save}>
            <Field label="Brand name"><input required value={form.brandName} onChange={set('brandName')} /></Field>
            <Field label="Salt / generic name"><input value={form.genericName || ''} onChange={set('genericName')} /></Field>
            <Field label="Manufacturer"><input value={form.manufacturer || ''} onChange={set('manufacturer')} /></Field>
            <Field label="Dosage form"><input value={form.dosageForm || ''} onChange={set('dosageForm')} /></Field>
            <Field label="Units per pack"><input type="number" min="1" required value={form.unitsPerPack} onChange={set('unitsPerPack')} /></Field>
            <Field label="HSN code"><input value={form.hsnCode || ''} onChange={set('hsnCode')} /></Field>
            <Field label="GST %"><select value={Number(form.gstPercent)} onChange={set('gstPercent')}>{[0, 5, 12, 18, 28].map((g) => <option key={g} value={g}>{g}</option>)}</select></Field>
            <Field label="Schedule">
              <select value={form.schedule} onChange={set('schedule')}>{['OTC', 'G', 'H', 'H1', 'X', 'NDPS'].map((s) => <option key={s}>{s}</option>)}</select>
            </Field>
            <Field label="Storage">
              <select value={form.storage || 'ROOM'} onChange={set('storage')}><option value="ROOM">Room temperature</option><option value="COLD">Cold chain (2 to 8 C)</option><option value="PROTECT_LIGHT">Protect from light</option></select>
            </Field>
            <Field label="Reorder level (units)"><input type="number" min="0" value={form.reorderLevel} onChange={set('reorderLevel')} /></Field>
            <Field label="Barcode" full><input value={form.barcode || ''} onChange={set('barcode')} /></Field>
            <div className="full row"><button className="btn">Save medicine</button></div>
          </form>
        </Modal>
      )}
    </>
  );
}
