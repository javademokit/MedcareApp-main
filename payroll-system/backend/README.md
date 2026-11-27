# Payroll system backend

The React Payroll Desk uses this Spring Boot service for employee, attendance,
salary-structure, and payroll data. Run it alongside the Medcare API; the
employee endpoints validate the existing Medcare session and require the
matching CSRF token. Onboarding and employee-document routes are limited to HR
and hospital administrator roles. The same access rules apply to profile
pictures.

## Employee onboarding documents

The onboarding API accepts Aadhaar cards, PAN cards, previous salary slips, and
degree certificates as PDF, JPEG, or PNG files up to 8 MB each. Files are stored outside MongoDB in
employee-ID folders under:

```text
~/.payroll-system/employee-documents/{employeeId}/
```

Documents are encrypted with AES-GCM before being written. The backend creates
the encryption key at `~/.payroll-system/employee-documents.key` and restricts
both the key and document files to the current operating-system user where
POSIX permissions are supported. Keep the key file with the local installation;
documents cannot be decrypted if it is lost. Do not commit the storage folder
or key to source control.

Override the defaults with `PAYROLL_DOCUMENT_STORAGE_DIR` and
`PAYROLL_DOCUMENT_KEY_FILE`. The payroll service binds to `127.0.0.1` by
default; set `PAYROLL_SERVER_ADDRESS` only when a different bind address is
deliberately required. The Medcare session verifier defaults to
`http://localhost:7771/api` and can be changed with
`MEDCARE_AUTH_API_BASE_URL`.

The onboarding endpoints are:

- `POST /api/employees/onboard`
- `PUT /api/employees/{id}/onboard`
- `GET /api/employees/{id}/documents`
- `POST /api/employees/{id}/documents/{aadhaar|pan|salary-slip|degree}`
- `GET /api/employees/{id}/documents/{type}/download`
- `DELETE /api/employees/{id}/documents/{type}`
- `POST /api/employees/{id}/profile-picture`
- `GET /api/employees/{id}/profile-picture`

Employee IDs are assigned by this backend: doctors and consultants use
`DT-` plus six digits, nurses use `NR-` plus six digits, and other employees
use `EMP-` plus six digits. Existing employee IDs are not changed.
Optional payroll bank details include the account number, IFSC code, branch
name, and city. IFSC codes are validated and saved in uppercase.

Profile pictures accept JPEG or PNG images up to 5 MB. Their bytes and content
type are stored in the `employee_profile_pictures` MongoDB collection.
The document API stores generated filenames and metadata only; it does not
retain the client-provided filename.
