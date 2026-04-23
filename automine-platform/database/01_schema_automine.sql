CREATE DATABASE IF NOT EXISTS automine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE automine;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL
) ENGINE=InnoDB;

CREATE TABLE permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL UNIQUE,
    module_name VARCHAR(80) NOT NULL,
    action_name VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB;

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    username VARCHAR(60) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMP NULL,
    password_reset_token VARCHAR(255),
    password_reset_expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
) ENGINE=InnoDB;

CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE employees (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    employee_code VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    document_number VARCHAR(30) NOT NULL UNIQUE,
    position VARCHAR(120) NOT NULL,
    base_salary DECIMAL(14,2) NOT NULL,
    hire_date DATE NOT NULL,
    termination_date DATE NULL,
    phone VARCHAR(30),
    address VARCHAR(180),
    emergency_contact VARCHAR(180),
    emergency_phone VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_employee_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'RETIRED'))
) ENGINE=InnoDB;

CREATE INDEX idx_employees_status ON employees(status);
CREATE INDEX idx_employees_hire_date ON employees(hire_date);

CREATE TABLE payroll_periods (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_code VARCHAR(20) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    payment_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT chk_period_status CHECK (status IN ('OPEN', 'CLOSED', 'PAID'))
) ENGINE=InnoDB;

CREATE TABLE payroll_entries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payroll_period_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    base_salary DECIMAL(14,2) NOT NULL,
    overtime_hours DECIMAL(8,2) NOT NULL DEFAULT 0,
    overtime_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    bonus_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    deduction_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    net_pay DECIMAL(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CALCULATED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_payroll_entries_period FOREIGN KEY (payroll_period_id) REFERENCES payroll_periods(id),
    CONSTRAINT fk_payroll_entries_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT chk_payroll_entry_status CHECK (status IN ('CALCULATED', 'APPROVED', 'PAID'))
) ENGINE=InnoDB;

CREATE UNIQUE INDEX ux_payroll_entry_period_employee ON payroll_entries(payroll_period_id, employee_id);

CREATE TABLE bank_deposits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    payroll_entry_id BIGINT NULL,
    bank_name VARCHAR(120) NOT NULL,
    account_number VARCHAR(40) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    account_holder VARCHAR(160) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    receipt_url VARCHAR(255),
    deposit_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_bank_deposits_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_bank_deposits_payroll FOREIGN KEY (payroll_entry_id) REFERENCES payroll_entries(id),
    CONSTRAINT chk_account_type CHECK (account_type IN ('SAVINGS', 'CURRENT')),
    CONSTRAINT chk_deposit_status CHECK (status IN ('REGISTERED', 'VERIFIED', 'REJECTED'))
) ENGINE=InnoDB;

CREATE INDEX idx_deposits_employee_date ON bank_deposits(employee_id, deposit_date);

CREATE TABLE labor_certificates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    certificate_code VARCHAR(70) NOT NULL UNIQUE,
    issue_date DATE NOT NULL,
    validation_code VARCHAR(100) NOT NULL UNIQUE,
    pdf_url VARCHAR(255),
    public_query_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_labor_cert_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE inventory_products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(120),
    unit_measure VARCHAR(20) NOT NULL,
    min_stock DECIMAL(14,2) NOT NULL DEFAULT 0,
    current_stock DECIMAL(14,2) NOT NULL DEFAULT 0,
    unit_cost DECIMAL(14,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT chk_product_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB;

CREATE TABLE inventory_movements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity DECIMAL(14,2) NOT NULL,
    reference_module VARCHAR(60),
    reference_id BIGINT,
    notes VARCHAR(255),
    movement_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_inventory_movements_product FOREIGN KEY (product_id) REFERENCES inventory_products(id),
    CONSTRAINT chk_movement_type CHECK (movement_type IN ('ENTRY', 'EXIT', 'ADJUSTMENT'))
) ENGINE=InnoDB;

CREATE INDEX idx_inventory_product_type_date ON inventory_movements(product_id, movement_type, movement_date);

CREATE TABLE mining_production (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    production_date DATE NOT NULL,
    shift_name VARCHAR(20) NOT NULL,
    tons_extracted DECIMAL(14,2) NOT NULL,
    supervisor_employee_id BIGINT NULL,
    machinery_used VARCHAR(200),
    observations VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_mining_production_supervisor FOREIGN KEY (supervisor_employee_id) REFERENCES employees(id),
    CONSTRAINT chk_shift_name CHECK (shift_name IN ('MORNING', 'AFTERNOON', 'NIGHT'))
) ENGINE=InnoDB;

CREATE INDEX idx_mining_production_date_shift ON mining_production(production_date, shift_name);

CREATE TABLE sgsst_incidents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id BIGINT NULL,
    incident_type VARCHAR(30) NOT NULL,
    report_date DATE NOT NULL,
    event_date DATE NOT NULL,
    severity_level VARCHAR(20) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    disability_days INT NOT NULL DEFAULT 0,
    risk_type VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_incidents_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT chk_incident_type CHECK (incident_type IN ('ACCIDENT', 'INCIDENT')),
    CONSTRAINT chk_severity_level CHECK (severity_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED'))
) ENGINE=InnoDB;

CREATE TABLE ppe_deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    delivery_date DATE NOT NULL,
    ppe_name VARCHAR(120) NOT NULL,
    quantity INT NOT NULL,
    condition_notes VARCHAR(255),
    digital_signature_url VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_ppe_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE INDEX idx_ppe_employee_date ON ppe_deliveries(employee_id, delivery_date);

CREATE TABLE accounting_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_date DATE NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    amount DECIMAL(14,2) NOT NULL,
    reference_number VARCHAR(80),
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT chk_transaction_type CHECK (transaction_type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT chk_transaction_status CHECK (status IN ('POSTED', 'VOID'))
) ENGINE=InnoDB;

CREATE INDEX idx_accounting_type_date ON accounting_transactions(transaction_type, transaction_date);

CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id BIGINT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(500) NOT NULL,
    send_channel VARCHAR(30) NOT NULL,
    sent_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    created_by VARCHAR(120) NULL,
    updated_by VARCHAR(120) NULL,
    CONSTRAINT fk_notifications_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    event_type VARCHAR(60) NOT NULL,
    module_name VARCHAR(60) NOT NULL,
    entity_name VARCHAR(120) NOT NULL,
    entity_id VARCHAR(60),
    action VARCHAR(40) NOT NULL,
    ip_address VARCHAR(60),
    user_agent VARCHAR(255),
    payload_json JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE INDEX idx_audit_module_created ON audit_logs(module_name, created_at);

SET FOREIGN_KEY_CHECKS = 1;
