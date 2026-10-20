# QuerySleuth Cloud Deployment Guide (Free Hosting)

This guide walks you through deploying the QuerySleuth backend and web app to **free cloud hosting platforms** using our optimized multi-stage Docker container.

---

## 🎯 Best Free Hosting Platform Options

| Platform | Free Tier Specifications | Container Support | Setup Effort |
| :--- | :--- | :--- | :--- |
| **Render** *(Recommended)* | 750 free instance hrs/month, Free SSL/HTTPS, 512 MB RAM | Native Docker via GitHub | **1-Click** (uses [`render.yaml`](file:///d:/Nikson/querysleuth/render.yaml)) |
| **Koyeb** | Free "Eco" Nano instance forever, Global edge network | Native Docker via GitHub | 2 Minutes |
| **Railway** | Starter trial hours, automatic domain generation | Native Docker via GitHub | 2 Minutes |

---

## 🚀 Option 1: Deploy to Render (Recommended - 1-Click with `render.yaml`)

Render is the most reliable free hosting platform for containerized Spring Boot applications.

### Step 1: Push your project to GitHub
1. Create a repository on [GitHub](https://github.com/new) (e.g. `querysleuth`).
2. Push your code:
   ```bash
   git init
   git add .
   git commit -m "feat: complete querysleuth engine, extension, and docker deployment"
   git branch -M main
   git remote add origin https://github.com/<your-username>/querysleuth.git
   git push -u origin main
   ```

### Step 2: Connect to Render
1. Go to [render.com](https://render.com) and sign up / log in with GitHub.
2. In the Render Dashboard, click **New +** > **Blueprint**.
3. Select your `querysleuth` repository.
4. Render will automatically detect [`render.yaml`](file:///d:/Nikson/querysleuth/render.yaml) and configure:
   - **Service Name**: `querysleuth`
   - **Environment**: Docker (`Dockerfile`)
   - **Plan**: Free
   - **Health Check**: `/api/v1/health`
5. Click **Apply**. Render will build the multi-stage Docker image and deploy it.

Once deployed, Render provides a public HTTPS URL:
👉 **`https://querysleuth-<your-id>.onrender.com`**

---

## 🚀 Option 2: Deploy to Koyeb

1. Sign up at [koyeb.com](https://www.koyeb.com).
2. Click **Create App**.
3. Select **GitHub** as the deployment method and choose your repository.
4. Set the builder type to **Dockerfile**.
5. Select the **Eco (Free)** instance type.
6. Click **Deploy**.

---

## 🧪 How to Test Your Deployed Cloud Service

### 1. Test the Interactive Web UI
Open your live URL in any desktop or mobile browser:
```
https://<your-service-name>.onrender.com/
```
You will see the interactive dark-mode QuerySleuth Plan Explorer running live on the internet!

### 2. Test the Health Endpoint
```bash
curl https://<your-service-name>.onrender.com/api/v1/health
```
**Expected Response:**
```json
{
  "status": "UP",
  "service": "QuerySleuth",
  "version": "0.0.1-SNAPSHOT"
}
```

### 3. Test Plan Analysis API
```bash
curl -X POST https://<your-service-name>.onrender.com/api/v1/plans/analyze \
  -H "Content-Type: text/plain" \
  --data "Function Scan on dbo.gettariffmainsearchsinglerow_db (actual time=402.410..402.566 rows=2000 loops=1)
 Buffers: shared hit=233054 read=4, local hit=20729"
```

---

## 🔌 Connecting Your Chrome Extension to the Cloud

Once your backend is deployed:
1. Open the QuerySleuth Chrome Extension (or Side Panel).
2. Click the **Settings icon (⚙️)** in the top-right corner.
3. Paste your live cloud URL (e.g. `https://querysleuth.onrender.com`).
4. Click **Save**.

The extension status badge will turn green (**Online**), allowing you to diagnose PostgreSQL queries from anywhere without running a local Spring Boot server!
