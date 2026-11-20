create table cluster_search (id varchar(255) not null, payload longtext not null, created_at datetime not null, expire_at datetime not null, primary key (id));
create index idx_cluster_search_expire_at on cluster_search (expire_at);
