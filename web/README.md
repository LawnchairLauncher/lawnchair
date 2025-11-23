# ONE Web Interface

Minimal chat interface for Claude API.

## Quick Deploy (Mobile-Friendly)

### 1. Cloudflare Pages
1. Go to [dash.cloudflare.com](https://dash.cloudflare.com)
2. Pages → Create project → Connect to Git
3. Select this repo
4. Build settings:
   - Build command: `npm run build`
   - Output directory: `dist`
5. Deploy

### 2. IONOS DNS
1. Go to [my.ionos.com](https://my.ionos.com)
2. Domains → Your domain → DNS
3. Add CNAME record:
   - Host: `@`
   - Points to: `your-project.pages.dev`

---

## Automated Setup (Desktop Only)

For redeployment, domain changes, or setting up additional instances.

Requires: Node.js, desktop browser (Playwright)

```bash
cd web
npm install
npm run setup
```

Opens `http://localhost:3001` with a form to automate:
- Cloudflare Pages connection
- IONOS DNS configuration

**Note:** Browser opens visibly for OAuth authorization steps.

---

## Local Development

```bash
npm install
npm run dev
```

Opens at `http://localhost:5173`
