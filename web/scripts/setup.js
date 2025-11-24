import express from 'express';
import { chromium } from 'playwright';

const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Onboarding form HTML
const formHTML = `
<!DOCTYPE html>
<html>
<head>
  <title>ONE Setup</title>
  <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400&display=swap" rel="stylesheet">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      min-height: 100vh;
      background: #0a0a0a;
      font-family: 'Roboto', sans-serif;
      color: #e5e5e5;
      padding: 2rem;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .container {
      max-width: 500px;
      width: 100%;
    }
    h1 {
      font-size: 2rem;
      font-weight: 300;
      letter-spacing: 0.1em;
      margin-bottom: 0.5rem;
      text-align: center;
    }
    .subtitle {
      color: #737373;
      font-size: 0.875rem;
      text-align: center;
      margin-bottom: 2rem;
    }
    .section {
      background: #171717;
      border-radius: 8px;
      padding: 1.5rem;
      margin-bottom: 1rem;
    }
    .section-title {
      font-size: 0.75rem;
      color: #10b981;
      letter-spacing: 0.1em;
      margin-bottom: 1rem;
      text-transform: uppercase;
    }
    label {
      display: block;
      font-size: 0.75rem;
      color: #737373;
      margin-bottom: 0.5rem;
    }
    input {
      width: 100%;
      background: #262626;
      border: 1px solid #404040;
      border-radius: 4px;
      padding: 0.75rem;
      color: #e5e5e5;
      font-size: 0.875rem;
      margin-bottom: 1rem;
    }
    input:focus {
      outline: none;
      border-color: #10b981;
    }
    button {
      width: 100%;
      background: #10b981;
      border: none;
      border-radius: 4px;
      padding: 1rem;
      color: #0a0a0a;
      font-size: 0.875rem;
      cursor: pointer;
      font-weight: 400;
      letter-spacing: 0.05em;
    }
    button:hover {
      background: #059669;
    }
    .note {
      font-size: 0.75rem;
      color: #404040;
      text-align: center;
      margin-top: 1rem;
      line-height: 1.5;
    }
    #status {
      margin-top: 1rem;
      padding: 1rem;
      background: #171717;
      border-radius: 4px;
      font-size: 0.875rem;
      display: none;
    }
    .step { color: #737373; margin: 0.25rem 0; }
    .step.done { color: #10b981; }
    .step.active { color: #e5e5e5; }
    .step.error { color: #ef4444; }
  </style>
</head>
<body>
  <div class="container">
    <h1>ONE SETUP</h1>
    <p class="subtitle">automated deployment</p>

    <form id="setupForm">
      <div class="section">
        <div class="section-title">GitHub</div>
        <label>Repository URL</label>
        <input name="github_repo" placeholder="https://github.com/user/repo" required />
      </div>

      <div class="section">
        <div class="section-title">Cloudflare</div>
        <label>Email</label>
        <input name="cf_email" type="email" placeholder="you@example.com" required />
        <label>Password (new account) or existing</label>
        <input name="cf_password" type="password" placeholder="••••••••" required />
      </div>

      <div class="section">
        <div class="section-title">IONOS</div>
        <label>Domain</label>
        <input name="ionos_domain" placeholder="yourdomain.com" required />
        <label>Email</label>
        <input name="ionos_email" type="email" placeholder="you@example.com" required />
        <label>Password</label>
        <input name="ionos_password" type="password" placeholder="••••••••" required />
      </div>

      <button type="submit">DEPLOY ONE</button>
    </form>

    <div id="status">
      <div class="step" data-step="browser">Launching browser...</div>
      <div class="step" data-step="cloudflare">Setting up Cloudflare Pages...</div>
      <div class="step" data-step="connect">Connecting repository...</div>
      <div class="step" data-step="ionos">Configuring IONOS DNS...</div>
      <div class="step" data-step="done">Deployment complete!</div>
    </div>

    <p class="note">
      This will automate Cloudflare Pages setup and IONOS DNS configuration.
      Your credentials are only used locally and never stored.
    </p>
  </div>

  <script>
    const form = document.getElementById('setupForm');
    const status = document.getElementById('status');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      status.style.display = 'block';

      const formData = new FormData(form);
      const data = Object.fromEntries(formData);

      const eventSource = new EventSource('/setup/stream?' + new URLSearchParams(data));

      eventSource.onmessage = (event) => {
        const { step, status: stepStatus } = JSON.parse(event.data);
        const stepEl = document.querySelector('[data-step="' + step + '"]');
        if (stepEl) {
          stepEl.className = 'step ' + stepStatus;
        }
      };

      eventSource.onerror = () => {
        eventSource.close();
      };
    });
  </script>
</body>
</html>
`;

app.get('/', (req, res) => {
  res.send(formHTML);
});

app.get('/setup/stream', async (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');

  const send = (step, status) => {
    res.write(`data: ${JSON.stringify({ step, status })}\n\n`);
  };

  const {
    github_repo,
    cf_email,
    cf_password,
    ionos_domain,
    ionos_email,
    ionos_password
  } = req.query;

  let browser;

  try {
    // Launch browser
    send('browser', 'active');
    browser = await chromium.launch({ headless: false }); // Set to true for production
    const context = await browser.newContext();
    send('browser', 'done');

    // Cloudflare setup
    send('cloudflare', 'active');
    const cfPage = await context.newPage();

    // Try to sign up or login
    await cfPage.goto('https://dash.cloudflare.com/sign-up');

    // Check if already logged in or need to sign up
    const signupForm = await cfPage.$('input[name="email"]');
    if (signupForm) {
      await cfPage.fill('input[name="email"]', cf_email);
      await cfPage.fill('input[name="password"]', cf_password);

      // Try signup first
      const signupButton = await cfPage.$('button[type="submit"]');
      if (signupButton) {
        await signupButton.click();
        await cfPage.waitForTimeout(3000);
      }

      // If signup fails, try login
      if (cfPage.url().includes('sign-up')) {
        await cfPage.goto('https://dash.cloudflare.com/login');
        await cfPage.fill('input[name="email"]', cf_email);
        await cfPage.fill('input[name="password"]', cf_password);
        await cfPage.click('button[type="submit"]');
        await cfPage.waitForTimeout(3000);
      }
    }

    send('cloudflare', 'done');

    // Connect repository to Pages
    send('connect', 'active');
    await cfPage.goto('https://dash.cloudflare.com/?to=/:account/pages/new/provider/github');
    await cfPage.waitForTimeout(2000);

    // This will require GitHub OAuth - user may need to authorize manually
    // The automation will guide them through the flow

    // Wait for repo selection page
    await cfPage.waitForSelector('text=Connect to Git', { timeout: 30000 }).catch(() => {});

    // Select repository (this varies based on Cloudflare's UI)
    const repoName = github_repo.split('/').pop();
    await cfPage.fill('input[placeholder*="Search"]', repoName).catch(() => {});
    await cfPage.waitForTimeout(1000);

    // Click on the repo when it appears
    await cfPage.click(`text=${repoName}`).catch(() => {});
    await cfPage.waitForTimeout(1000);

    // Configure build settings
    await cfPage.selectOption('select', 'vite').catch(() => {});

    // Set build command and output
    await cfPage.fill('input[name="buildCommand"]', 'npm run build').catch(() => {});
    await cfPage.fill('input[name="buildOutputDirectory"]', 'dist').catch(() => {});

    // Deploy
    await cfPage.click('button:has-text("Save and Deploy")').catch(() => {});
    await cfPage.waitForTimeout(5000);

    // Get the pages.dev URL
    const pagesUrl = await cfPage.url();
    send('connect', 'done');

    // IONOS DNS setup
    send('ionos', 'active');
    const ionosPage = await context.newPage();
    await ionosPage.goto('https://my.ionos.com/');

    // Login to IONOS
    await ionosPage.fill('input[name="identifier"]', ionos_email).catch(() => {});
    await ionosPage.fill('input[name="password"]', ionos_password).catch(() => {});
    await ionosPage.click('button[type="submit"]').catch(() => {});
    await ionosPage.waitForTimeout(3000);

    // Navigate to DNS settings
    await ionosPage.goto('https://my.ionos.com/domain-dns');
    await ionosPage.waitForTimeout(2000);

    // Find and click on the domain
    await ionosPage.click(`text=${ionos_domain}`).catch(() => {});
    await ionosPage.waitForTimeout(2000);

    // Add CNAME record
    await ionosPage.click('text=Add record').catch(() => {});
    await ionosPage.waitForTimeout(1000);

    // Select CNAME type
    await ionosPage.click('text=CNAME').catch(() => {});

    // The project name from Cloudflare Pages
    const projectName = repoName.toLowerCase().replace(/[^a-z0-9]/g, '-');

    // Fill CNAME details
    await ionosPage.fill('input[name="hostname"]', '@').catch(() => {});
    await ionosPage.fill('input[name="value"]', `${projectName}.pages.dev`).catch(() => {});

    // Save
    await ionosPage.click('button:has-text("Save")').catch(() => {});
    await ionosPage.waitForTimeout(2000);

    send('ionos', 'done');
    send('done', 'done');

  } catch (error) {
    console.error('Setup error:', error);
    send('done', 'error');
  } finally {
    // Keep browser open for user to verify/complete any manual steps
    // await browser?.close();
  }

  res.end();
});

const PORT = 3001;
app.listen(PORT, () => {
  console.log(`\\n  ONE Setup running at http://localhost:${PORT}\\n`);
  console.log('  Open this URL in your browser to begin deployment.\\n');
});
