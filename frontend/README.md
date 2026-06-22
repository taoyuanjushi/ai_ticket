# AI Ticket Frontend

React/Vite frontend for the AI-Enhanced Ticket Management System.

## Run

```powershell
npm.cmd install
npm.cmd run dev
```

The dev server proxies:

- `/api` to Java Spring Boot `http://127.0.0.1:8080`

AI requests are sent to Java `/ai/*`; Java forwards trusted user identity to the Python AI service.

For local UI testing without backends:

```powershell
$env:VITE_MOCK_API='true'; npm.cmd run dev
```

## Scripts

```powershell
npm.cmd run build
npm.cmd run test
npm.cmd run lint
```
