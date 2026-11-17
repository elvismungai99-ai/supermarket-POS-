# Infrastructure Services

## PostgreSQL Database

Managed via Docker Compose.

### Start Database
```bash
docker compose -f infra/docker-compose.yml up -d
```

### Check Status & Health
```bash
docker compose -f infra/docker-compose.yml ps
```

### Stop Database
```bash
docker compose -f infra/docker-compose.yml down
```

### Database Credentials
- **Host**: `localhost`
- **Port**: `5433`
- **Database**: `pos`
- **User**: `pos`
- **Password**: `pos_dev_password`
- **URL**: `postgresql://pos:pos_dev_password@localhost:5433/pos`

### Direct CLI Access
```bash
docker compose -f infra/docker-compose.yml exec db psql -U pos -d pos
```
