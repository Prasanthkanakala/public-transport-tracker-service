# How to Run the Public Transport Tracker Application

## Prerequisites

- **Java 17+** installed
- **Gradle** installed (or use Gradle wrapper)
- **Node.js 16+** and **npm** installed
- **Two terminal windows** (one for backend, one for frontend)

## Step 1: Start the Backend Server

### Using PowerShell (Windows)

```powershell
# Open PowerShell terminal
# Navigate to the backend directory
cd "public transport tracker service/backend"

# Run the Spring Boot application
gradle bootRun
```

### Expected Output:

You should see:
```
> Task :bootRun

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::

... Started TransportTrackerApplication
```

**Backend is now running on:** `http://localhost:8080`

## Step 2: Start the Frontend Server

### Open a NEW Terminal Window

```powershell
# Navigate to the frontend directory
cd "public transport tracker service/frontend"

# Install dependencies (first time only)
npm install

# Start the React development server
npm start
```

**Frontend is now running on:** `http://localhost:3000`

Your browser should automatically open to `http://localhost:3000`

## Step 3: Verify It's Working

### Check the Frontend:

1. Open `http://localhost:3000` in your browser
2. Look for the **data source badge** in the header
3. It should show **`LIVE`** (not `MOCK`)
4. You should see:
   - Vehicle positions on the map
   - Arrival predictions
   - Service alerts

### Check the Backend Logs:

In the backend terminal, you should see logs like:
```
TransitLand: fetching routes for operator o-dr5r-nyct route A
TransitLand: Successfully received route data
TransitLand: Found route A - 8 Avenue Express
TransitLand: Returning 1 vehicle locations
```

## Troubleshooting

### Backend Won't Start

**Error:** `Port 8080 already in use`

**Solution:**
```powershell
# Find what's using port 8080
netstat -ano | findstr :8080

# Kill the process (replace PID with actual process ID)
taskkill /PID <PID> /F
```

### Frontend Won't Start

**Error:** `Port 3000 already in use`

**Solution:**
```powershell
# Kill process on port 3000
netstat -ano | findstr :3000
taskkill /PID <PID> /F
```

### Still Seeing Mock Data

**Check:**

1. **Backend is running:** Visit `http://localhost:8080/actuator/health`
2. **Frontend proxy is working:** Check browser Network tab for API calls
3. **Offline mode is OFF:** Toggle the offline switch in the UI
4. **Check backend logs:** Look for TransitLand API calls

## Stopping the Servers

### Stop Backend:
- Press `Ctrl + C` in the backend terminal

### Stop Frontend:
- Press `Ctrl + C` in the frontend terminal
- Type `Y` when asked "Terminate batch job?"

## Summary

✅ **Backend:** `cd backend && gradle bootRun` → http://localhost:8080  
✅ **Frontend:** `cd frontend && npm start` → http://localhost:3000  
✅ **Verify:** Data source badge shows `LIVE`  

**Enjoy your Public Transport Tracker! 🚇🚌🚊**
