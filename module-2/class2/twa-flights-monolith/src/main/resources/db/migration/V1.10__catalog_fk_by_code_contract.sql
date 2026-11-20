-- Stage 3 of the separation of the data of the catalog (M2 C2): the FKs by code of V1.9 are the only physical FKs of
-- the catalog (contract of expand/contract). Only the constraints are dropped: the columns country_id and
-- continent_id, their indexes and their triggers stay, because the entities of the two projects and the previous
-- version of the monolith (rolling deploy) still read them. They are dropped together with the tables of the catalog
-- in M2 C5 (D9 of ADR-002: no tables are dropped in this plan).
-- Dropping a FK is a change of metadata only (INPLACE): it does not block the readers.
-- Rollback: db/rollback/U1.10__catalog_fk_by_code_contract.sql
ALTER TABLE city DROP FOREIGN KEY FKrpd7j1p7yxr784adkx4pyepba, ALGORITHM = INPLACE, LOCK = NONE;
ALTER TABLE country DROP FOREIGN KEY FKpymfsgrl32dy3gtl9r7rykkjg, ALGORITHM = INPLACE, LOCK = NONE;
