-- =============================================================================
-- V21 -- Them Department.MANAGEMENT cho tai khoan Admin.
--
-- Truoc migration nay, Admin (khong thuoc phong ban nao that su) bi gan tam
-- vao Department.TRAINING vi cot employees.department la NOT NULL va enum chi
-- co 4 gia tri khop 4 vai tro nhan vien thuong. Hau qua: Employee.isTrainer()
-- (= department == TRAINING) tra ve true cho Admin, khien he thong coi Admin
-- la mot huan luyen vien that (PayrollService tinh hoa hong PT cho Admin,
-- UI hien tab "Lich day"/"Danh gia ve toi" cho Admin).
-- =============================================================================

ALTER TABLE employees DROP CONSTRAINT chk_emp_department;
ALTER TABLE employees ADD CONSTRAINT chk_emp_department
    CHECK (department IN ('TRAINING','SALES','FRONT_DESK','ACCOUNTING','MANAGEMENT'));

-- Sua du lieu da seed truoc do (neu co): tai khoan Admin tung bi gan tam vao
-- TRAINING, nay chuyen dung sang MANAGEMENT.
UPDATE employees e
SET department = 'MANAGEMENT'
FROM users u
WHERE u.person_id = e.person_id AND u.primary_role = 'ADMIN' AND e.department = 'TRAINING';
