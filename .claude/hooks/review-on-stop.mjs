// Claude Code Stop hook: before a session stops with uncommitted changes nobody has reviewed yet, it asks Claude to run
// the review-changes skill on them. Each change set is asked about once: its fingerprint (the diff against HEAD and the
// untracked files' contents) is kept in the git directory, so stopping again with the same changes, or straight after
// the review the hook asked for, goes ahead. Anything that goes wrong here lets the stop go ahead.
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const MARKER = 'review-changes.last';
/** The most git may print for one call, in bytes: a whole diff, binary files included. */
const MAX_OUTPUT = 256 * 1024 * 1024;

function readInput() {
  try {
    return JSON.parse(fs.readFileSync(0, 'utf8') || '{}');
  } catch {
    return {};
  }
}

function git(cwd, ...args) {
  const options = { cwd, encoding: 'buffer', maxBuffer: MAX_OUTPUT, stdio: ['ignore', 'pipe', 'ignore'] };
  return execFileSync('git', args, options);
}

/** The change set's fingerprint: the diff against HEAD, and every untracked file's name and contents. */
function fingerprint(top) {
  const hash = createHash('sha256').update(git(top, 'diff', 'HEAD', '--binary'));
  const listed = git(top, 'ls-files', '--others', '--exclude-standard', '-z').toString('utf8');
  const untracked = listed.split('\0').filter(Boolean);
  for (const file of untracked) {
    hash.update(file).update('\0').update(fs.readFileSync(path.join(top, file)));
  }
  return hash.digest('hex');
}

function main() {
  const input = readInput();
  // Already continuing because of a Stop hook: the review it asked for has run.
  if (input.stop_hook_active) {
    return;
  }
  const cwd = input.cwd || process.cwd();
  const top = git(cwd, 'rev-parse', '--show-toplevel').toString('utf8').trim();
  if (git(top, 'status', '--porcelain').length === 0) {
    return;
  }
  const marker = path.join(git(top, 'rev-parse', '--absolute-git-dir').toString('utf8').trim(), MARKER);
  const changes = fingerprint(top);
  if (fs.existsSync(marker) && fs.readFileSync(marker, 'utf8').trim() === changes) {
    return;
  }
  fs.writeFileSync(marker, changes + '\n');
  process.stdout.write(JSON.stringify({
    decision: 'block',
    reason: 'The working tree has uncommitted changes that have not been reviewed yet. Run the review-changes skill on '
      + 'them now and report what it finds. Do not commit unless you were asked to. (Project Stop hook; it asks once '
      + 'for each change set.)',
  }));
}

try {
  main();
} catch {
  // Not a git repository, no commits yet, or git missing: nothing to review.
}
