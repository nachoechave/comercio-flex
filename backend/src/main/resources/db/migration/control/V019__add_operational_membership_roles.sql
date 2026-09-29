ALTER TABLE memberships
    DROP CHECK ck_memberships_role,
    ADD CONSTRAINT ck_memberships_role
        CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'SELLER', 'STAFF'));
