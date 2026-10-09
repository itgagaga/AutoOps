# Super Employee Web

Vue 3 + Element Plus frontend for the Super Employee platform.

```powershell
npm install
npm run dev
```

Open `http://localhost:5173`. The Vite proxy forwards `/api` and `/actuator` to `http://localhost:8088` by default. Set `VITE_API_PROXY` in `.env` when the backend uses another address. In production, set `VITE_API_BASE_URL` to the deployed backend URL and set the backend `WEB_ORIGIN` variable to the frontend origin.
