# The KaizoCore page

A static page, one HTML file, deployable to Netlify from this folder
(`netlify deploy --prod --dir site` from the repo root once a site exists).
Nothing here is live until Blake deploys it.

Placeholders to fill before the first deploy, each in one place:

| Token | What |
|---|---|
| `RELEASES_URL` | the GitHub Releases page |
| `SUPPORT_URL` | Ko-fi or GitHub Sponsors |
| `SOURCE_URL` | the public repository |
| `DISCORD_URL` | the invite to the testers' channel |
| `LICENSE_LINE` | e.g. "GPL-3.0, source above" once the licence decision is made |

The bug form is a Netlify Form (`data-netlify="true"`), the same mechanism
willowcreek.group's audit uses; submissions land in the Netlify dashboard
and can be forwarded by email or webhook to a GitHub issue. No file uploads,
on purpose. `thanks.html` is the post-submit page.

`img/` takes the screenshot set (see tools/screenshots.sh) and `video/`
the 45-second cut; both are produced, not hand-made.
