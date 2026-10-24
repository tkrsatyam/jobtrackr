ALTER TABLE applications DROP CONSTRAINT IF EXISTS applications_source_check;
ALTER TABLE applications ADD CONSTRAINT applications_source_check CHECK (source IN (
    'LINKEDIN', 'NAUKRI', 'INDEED', 'INTERNSHALA', 'COMPANY_WEBSITE', 'REFERRAL', 'ANGEL_LIST', 'INSTAHYRE', 'EMAIL', 'OTHER'
));
