# Team Rules

1. Nobody pushes directly to `main`.
2. Create your own branch for every task. Format: `yourname-taskname`, for example `aryan-pylint-script`.
3. Every task becomes a small pull request, ideally finished in one or two hours.
4. The leader reviews every pull request the same day.
5. If you are stuck for more than one hour, message the group. Asking early is expected.
6. Daily 10 minute call: what I finished, what is next, what blocks me.
7. Work in your own folder. Do not edit someone else's files without telling them.
8. Never commit passwords, API keys or `.env` files.

## Git steps for every task
```
git checkout main
git pull
git checkout -b yourname-taskname
# make your change
git add .
git commit -m "Short message about what you did"
git push -u origin yourname-taskname
```
Then open a pull request on GitHub and fill in the template.

## Review checklist (leader)
- Does the task meet its "Done when" condition?
- Is the pull request small and focused on one task?
- Did the author touch only their own files?
- Say one good thing before asking for changes.
