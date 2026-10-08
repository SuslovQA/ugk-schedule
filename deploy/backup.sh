#!/usr/bin/env bash
set -euo pipefail
umask 077
backup_dir=/var/backups/ugk-schedule
install -d -o root -g root -m 0700 "$backup_dir"
stamp=$(date +%F-%H%M%S)
partial="$backup_dir/$stamp.dump.partial"
final="$backup_dir/$stamp.dump"
/usr/sbin/runuser -u postgres -- /usr/bin/pg_dump -Fc --no-owner --no-acl ugk_schedule > "$partial"
test -s "$partial"
/usr/bin/pg_restore --list "$partial" > /dev/null
mv "$partial" "$final"
# Fixed, root-controlled directory; retains at least 14 days.
find "$backup_dir" -maxdepth 1 -type f -name '*.dump' -mtime +14 -delete
