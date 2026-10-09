#!/usr/bin/env bash
set -euo pipefail
cd /opt/ugk-schedule
test -s app.jar.next
if [[ -f app.jar ]]; then
  cp app.jar app.jar.previous
fi
chmod 644 app.jar.next
mv -f app.jar.next app.jar
sudo -n /usr/bin/systemctl restart ugk-schedule.service

# The public API also queries PostgreSQL; a login page alone cannot check it.
readiness_deadline=$((SECONDS + 120))
attempt=1
while ((SECONDS < readiness_deadline)); do
  if /usr/bin/systemctl is-active --quiet ugk-schedule.service &&
      curl --fail --silent --show-error --max-time 3 \
        http://127.0.0.1:8080/api/public/levels > /dev/null; then
    echo 'Deployment is ready.'
    exit 0
  fi
  echo "Waiting for application readiness (attempt $attempt)..."
  attempt=$((attempt + 1))
  sleep 2
done
echo 'Deployment failed readiness check. Inspect journalctl; app.jar.previous is available for manual rollback.' >&2
exit 1
