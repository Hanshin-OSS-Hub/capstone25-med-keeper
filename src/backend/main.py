from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy import text
from database import get_db_async, AsyncSession
import models
from routers import users, drugs

app = FastAPI(
    title="Yakbot API Service",
    description="식약처 데이터를 활용한 알약 정보 제공 RESTful API 서비스",
    version="1.0.0",
)

app.include_router(users.router)
app.include_router(drugs.router)

@app.get("/")
async def root():
    return {"message": "Welcome to yakbot server!"}

@app.get("/yakbot")
async def get_yakbot_data(db: AsyncSession = Depends(get_db_async)):
    return {"message": "yakbot data placeholder"}

@app.get("/health")
async def check_db_health(db: AsyncSession = Depends(get_db_async)):
    try:
        await db.execute(text("SELECT 1"))
        return {
            "status": "ok",
            "database_connection": "success",
            "message": "Successfully connected to MySQL server."
        }
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail={
                "status": "error",
                "database_connection": "failure",
                "message": f"Database connection failed: {e}"
            }
        )
