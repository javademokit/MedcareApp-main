CREATE TABLE IF NOT EXISTS medical_appointment (
  id BIGSERIAL PRIMARY KEY,
  patient_name VARCHAR(255) NOT NULL,
  patient_email VARCHAR(255),
  patient_phone VARCHAR(50),
  doctor_name VARCHAR(255) NOT NULL,
  appointment_date DATE NOT NULL,
  appointment_time TIME NOT NULL,
  reason TEXT,
  status VARCHAR(50) NOT NULL DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'cancelled', 'completed', 'rescheduled')),
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX ON medical_appointment (patient_name);
CREATE INDEX ON medical_appointment (doctor_name);
CREATE INDEX ON medical_appointment (appointment_date);
CREATE INDEX ON medical_appointment (status);
